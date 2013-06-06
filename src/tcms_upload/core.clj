(ns tcms-upload.core
  (:require [clojure.java.io :as io]
            [necessary-evil.core :as rpc]
            [necessary-evil.fault :refer [fault?]]
            [clojure.set :refer [intersection rename project join rename-keys]]
            [clojure.core.contracts :as contracts]
            [clojure.xml :as xml]
            [clojure.tools.logging :as log]
            [slingshot.slingshot :refer [throw+]]
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
    (rpc.test-plan/get-test-cases connection [plan-id])
    set
    (project [:case_id :alias])
    (rename {:case_id :case})
    (join case-list)))

(defn map-project [map ks]
  (first (project #{map} ks)))



(defn product-and-version-id [con opts]
  (-> 
    (rpc.test-plan/get con [(:plan opts)])
    (map-project [:product_id :product_version_id])
    (rename-keys {:product_id :product, :product_version_id :product_version})))

(defn build-id [con opts]
  (->
    (rpc.build/check-build con [(:build-name opts) (:product opts)])
    (map-project [:build_id])
    (rename-keys {:build_id :build})))

(defn manager-id [con opts]
  (->
    (rpc.user/filter con [{:username__startswith (:manager-login opts)}])
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
    (map-project [:plan :build :manager :summary 
                  :product :product_version])
    (#(rpc.test-run/create con [%]))
    (map-project [:run_id])
    (rename-keys {:run_id :run})))


(defn create-test-run [con opts cases]
  (->> opts
    (resolv #{} new-test-run con) ;not checking run    
    (#(map (partial merge %) cases))
    (#(project % [:build :run :case :case_run_status]))
    (map #(rpc.test-case-run/create con [%]))
    doall))


(defn upload [con opts]
  (log/info "Loggin in with " con)
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
      (do
        (let [connection (project opts [:username :password :dry-run :rpc-url])]
          (upload connection opts))
        
      (println banner)))))
