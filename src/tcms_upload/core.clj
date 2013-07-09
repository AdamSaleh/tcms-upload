(ns tcms-upload.core
  (:require [clojure.java.io :as io]
            [necessary-evil.fault :refer [fault?]]
            [clojure.set :refer [union difference intersection rename project join rename-keys]]
            [clojure.xml :as xml]
            [tcms-upload.rpc.test-plan :as test-plan]
            [tcms-upload.rpc.build :as build]
            [tcms-upload.rpc.user :as user]
            [tcms-upload.rpc.test-run :as test-run]
            [tcms-upload.rpc.test-case-run :as test-case-run]
            [tcms-upload.rpc.test-case :as test-case]
            [clojure.tools.logging :as log]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.zip :as zip]
            [clojure.data.zip :as zf]
            [clojure.data.zip.xml :as zfx]
            [clojure.tools.cli :refer [cli]]
            [clojure.string :refer [split trim]]
            [clojure.walk :refer [postwalk prewalk] ])
  (:gen-class :main true))

(def status
    {:idle 1 "PASS" 2 "FAIL" 3
     :running 4 :paused 5
     :blocked 6
    :error 7 "SKIP" 8})


(defn side-print [x]
  (clojure.pprint/pprint x)
  x)

(defn merge-alias-status-with-same-uuid [test-list]
  (->> test-list 
    (map :alias) 
    (into #{})
    (map (fn [test-alias ] 
           [test-alias 
            (->> test-list (filter #(= test-alias (:alias %)))(map :name) first)
            (->> test-list (filter #(= test-alias (:alias %)))(map :log))
            (->> test-list (filter #(= test-alias (:alias %))) (map :case_run_status))]))
    (map (fn [[uuid name log status]]
           ;(when (< 1 (count status))
           ;  (log/info "Merging status of tests with uuid " uuid)) 
           (cond
             (every? #(= 2 %) status) [uuid name log 2]
             (some #(= 3 %) status) [uuid name log 3]
             :else [uuid name log 8])))
    (map (fn [[uuid name log status]]
           {:alias uuid, :name name,:log log :case_run_status status}))))

(defn get-alias-status-from-xml [filename]
  (->>
    (slurp filename)
    java.io.StringReader.
    org.xml.sax.InputSource.
    xml/parse
    (postwalk #(cond 
      (and (map? %) (contains? % :tag) (= :test-method (:tag %))
           (-> % :attrs :is-config)) 
        nil
      (and (map? %) (contains? % :tag) (= :test-method (:tag %))) 
        {:alias (if (contains? (:attrs %) :uuid ) 
                  (-> % :attrs :uuid) 
                  (-> % :attrs :description)) 
         :name (-> % :attrs :name) 
         :case_run_status (get status (-> % :attrs :status)) 
         :log (if (coll? %)
                (->> % :content
                  flatten
                  (map str)
                  clojure.string/join   
                  )
                (% :content))}
      (and (map? %) (contains? % :content)) 
        (into [] (:content %))  
      :else %))
    flatten
    (remove nil?)
    (filter map?)
   merge-alias-status-with-same-uuid)) 


(defn left-join [left right on]
  (let [unjoinable (difference (set (map on left)) (set (map on right)))] 
    unjoinable
    (union
      (join left right {on on})
      (filter #(unjoinable (on %)) left)
    )))

(defn create-unresolved-test-cases [connection opts case-list]
  (map 
    (fn [test-case]
      (if (contains? test-case :case)
        test-case
        (-> (test-case/create connection
             [{:product (opts :product) 
               :category 596 ;default, hardcoded 
               :alias (test-case :alias)
               :priority 1; P1 hardcoded
               :tag "tcms-uploader"
               :summary (test-case :name)
              }])
            (#(if (contains? % :case_id)
              (merge test-case {:case (:case_id %)})
              (do
               (log/info "Test case creation failed, not including")
               nil ))))))
    case-list))

(defn ensure-all-cases-are-in-plan [connection opts case-list]
  (let [cases-not-in-plan
        (into []
        (difference 
          (set (map :case case-list))
          (set (map :case_id 
            (test-plan/get-test-cases connection [(:plan opts)])))))]
    (test-case/link-plan connection [cases-not-in-plan (:plan opts)])
    case-list))

(defn process-test-case-ids [connection opts case-list]
  (->
    (map :alias case-list)
    ((partial remove #(= "" %)))
    ((partial map #(test-case/filter connection [{:alias %}])))
    doall
    ((partial map first))
    ((partial remove nil?))
    set
    (project [:case_id :alias])
    (rename {:case_id :case})
    (#(into #{} %))
    (#(left-join case-list % :alias))
    ((partial create-unresolved-test-cases connection opts))
    ((partial ensure-all-cases-are-in-plan connection opts))
    ))

(defn map-project [map ks]
  (first (project #{map} ks)))

(defn product-and-version-id [con opts]
  (-> 
    (test-plan/get con [(:plan opts)])
    (map-project [:product_id :product_version_id])
    (rename-keys {:product_id :product, :product_version_id :product_version})))

(defn build-id [con opts]
  (->
    (build/check-build con [(:build-name opts) (:product opts)])
    (#(if (contains? % :build_id) %
       (if (:dry-run con)
         (build/check-build con ["unspecified" (:product opts)])
         (build/create con [{:name (:build-name opts) :product (:product opts)}]))))
    (map-project [:build_id])
    (rename-keys {:build_id :build})))

(defn manager-id [con opts]
  (->
    (user/filter con [{:username__startswith (:manager-login opts)}])
    first
    (map-project [:id])
    (rename-keys {:id :manager})))

(defn resolv [verify res-fn con opts]
  (->
    (res-fn con opts)
    (#(do (log/info "Resolved params:" %) %))
    (#(if (= verify
             (intersection verify (into #{} (keys %)))) 
            %
            (throw+ (str "Couldn't resolve " verify))))
    (merge opts)))

(defn new-test-run [con opts]
  (cond 
    (contains? opts :run)
      opts
    :else (-> opts
      (map-project [:plan :build :manager :status :summary 
                    :product :product_version])
      (#(test-run/create con [(merge % {:tag "tcms-uploader"})]))
      (map-project [:run_id])
      (rename-keys {:run_id :run}))))

(defn test-run-status [cases con opts]
   (some->
    (test-plan/get-test-cases con [(:plan opts)])
    set
    (project [:case_id])
    (rename {:case_id :case})
    (#(clojure.set/subset? % (project cases [:case])))
    (#(if % {:status 1}
            {:status 0})))) ; if cases of plan are a subset of cases in xml return STOPPED (1)

(defn create-test-run [con opts cases]
  (->> opts
    (resolv #{:status} (partial test-run-status cases) con) 
    (resolv #{} new-test-run con) ;not checking run    
    (#(map (partial merge %) cases))
    (#(project % [:build :run :case :log :case_run_status]))
    (map (fn [tc] 
           (test-case-run/create con [(merge (dissoc tc :log) {:tag "tcms-uploader"})])
             ))
    doall
    (#(join cases % {:case :case_id}))
    (map (fn [tc] 
      (if (and (coll? (:log tc)) (not (empty (:log tc))))
          (doall (map 
            #(test-case-run/add-comment con [(:case_run_id tc) %]) 
            (filter #(not (string? %))        
            (:log tc))
                   ))
        ;else
        (when (string? (:log tc))
          #(test-case-run/add-comment con [(:case_run_id tc) (:log tc)]) 
        ))))
    doall
    ))

(defn upload [con opts]
  (let [xml (get-alias-status-from-xml (:xml-result opts))
        resolved-opts 
          (->>  opts
		(resolv #{:product :product_version} product-and-version-id con)
		(resolv #{:build} build-id con)
		(resolv #{:manager} manager-id con))]
        (->>
          xml
          (#(do (log/info "XML:" %) %))
          (process-test-case-ids con resolved-opts)
          (#(do (log/info "Test cases:" %) %))
          (create-test-run con resolved-opts))))

(defn try-upload [con opts]
  (try+
    (upload con opts)
    (catch Object _
                 (log/error (:message &throw-context)))))

(defn -main
  "I don't do a whole lot."
  [& args]
  (let [[opts args banner]
        (cli args
             ["-h" "--help" "Show help" :flag true :default false]
             ["-D" "--dry-run" "no modifying rpc calls are made" :flag true :default false]
             ["-u" "--username" "tcms username"]
             ["-p" "--password" "tcms password"]
             ["-r" "--rpc-url" "Url of tcms xmlrpc" :default "https://tcms.engineering.redhat.com/xmlrpc/"]
             ["-x" "--xml-result" "Result xml"]
             ["-P" "--plan" "Id of the test plan"]
             ["-R" "--run" "Id of the test run"]
             ["-B" "--build-name" "Name of the build"]
             ["-M" "--manager-login" "managers tcms login" ]
             ["-S" "--summary" "test-run summary" ])]
    (when (:help opts)
      (println banner)
      (System/exit 0))
    (if
        (and
         (:username opts) (:password opts) (:rpc-url opts)
         (:xml-result opts) (:plan opts) (:build-name opts)
         (:manager-login opts) (:summary opts))
      
        (let [connection (map-project opts [:username :password :dry-run :rpc-url])]
            (try-upload connection opts))    
        (do 
          (when (not (:username opts)) (println "Tcms username not set"))
          (when (not (:password opts)) (println "Tcms password not set"))
          (when (not (:xml-result opts)) (println "Test-ng result xml path not set"))
          (when (not (:plan opts)) (println "Tcms test plan id not set"))
          (when (not (:build-name opts)) (println "Build-name not set"))
          (when (not (:manager-login opts)) (println "Tcms manager's login not set"))
          (when (not (:summary opts)) (println "Test run summary not set"))
          (println banner)))))
;java -jar tcms-upload-0.1.0-SNAPSHOT-standalone.jar 
;--dry-run --username asaleh --password #Nitrate1 --xml-result /home/asaleh/clean-room/tcms-upload/testng-results.xml 
;--plan 9023 --build-name unspecified --manager-login asaleh --summary test23

