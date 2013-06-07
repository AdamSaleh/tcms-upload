(ns tcms-upload.core
  (:require [clojure.java.io :as io]
            [necessary-evil.fault :refer [fault?]]
            [clojure.set :refer [intersection rename project join rename-keys]]
            [clojure.xml :as xml]
            [tcms-upload.rpc.test-plan :as test-plan]
            [tcms-upload.rpc.build :as build]
            [tcms-upload.rpc.user :as user]
            [tcms-upload.rpc.test-run :as test-run]
            [tcms-upload.rpc.test-case-run :as test-case-run]
            [clojure.tools.logging :as log]
            [slingshot.slingshot :refer [try+ throw+]]
            [clojure.zip :as zip]
            [clojure.data.zip :as zf]
            [clojure.data.zip.xml :as zfx]
            [clojure.tools.cli :refer [cli]]
            [clojure.string :refer [split trim]]
            [clojure.walk :refer [postwalk] ])
  (:gen-class :main true))

(def status
    {:idle 1 "PASS" 2 "FAIL" 3
     :running 4 :paused 5
     :blocked 6
    :error 7 "SKIP" 8})

(defn get-alias-status-from-xml [filename]
  (->>
    (slurp filename)
    java.io.StringReader.
    org.xml.sax.InputSource.
    xml/parse
    (postwalk #(cond 
      (and (map? %) (contains? % :tag) (= :reporter-output (:tag %))) 
        nil
      (and (map? %) (contains? % :tag) (= :test-method (:tag %))) 
        {:alias (-> % :attrs :description) :case_run_status (get status (-> % :attrs :status)) }
      (and (map? %) (contains? % :content)) 
        (into [] (:content %))  
      :else %))
    flatten
    (filter #(not (nil? %)))))

(defn side-print [x]
  (print x)
  x)

(defn process-test-case-ids [connection plan-id case-list]
  (some->
    (test-plan/get-test-cases connection [plan-id])
    set
    (project [:case_id :alias])
    (rename {:case_id :case})
    (join case-list)))

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
  (-> opts
    (map-project [:plan :build :manager :status :summary 
                  :product :product_version])
    (#(test-run/create con [%]))
    (map-project [:run_id])
    (rename-keys {:run_id :run})))

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
    (#(project % [:build :run :case :case_run_status]))
    (map #(test-case-run/create con [%]))
    doall
    log/info))

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
          (process-test-case-ids con (:plan resolved-opts))
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

