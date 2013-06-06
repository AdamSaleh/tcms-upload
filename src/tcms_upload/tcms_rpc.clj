(ns tcms-upload.tcms-rpc
    (:require [clojure.core.contracts :as contracts]
              [clojure.tools.logging :as log]
              [necessary-evil.core :as rpc]
              [clojure.core.logic :as l]
              [slingshot.slingshot :refer [throw+]]
              [clojure.set :refer [intersection]]
              [necessary-evil.fault :refer [fault?]]))


;:TestCaseRun.create [{:build 2565 :run :case :case_run_status }])
;(:id (first (my-call :User.filter [{:username__startswith user}]))))
;(:id (first (my-call :Product.filter [{:name__startswith "katello"}])))
;:Product.filter_versions [{:product 313}]


(defn b-call [command connection args]
  (necessary-evil.value/allow-nils true
    (rpc/call* 
      (connection :rpc-url)
      command args
      :post-fn clj-http.client/post
      :request {:basic-auth [(connection :username) (connection :password)]
            :insecure? true})))

(defn call [modifying command connection args]
      (if (and modifying (:dry-run connection))
        (do
          (log/info "Dry calling:" command " with " args)
          {})
        (do
          (log/info "Calling:" command " with " args)
          (let [res (b-call command connection args)]
             (if (fault? res)
                (throw+ res)
                res)))))

(defmacro defrpc-call [name])

(defn single? [a]
  (= 1 (count a)))

(defmacro map-check [map-features & predicates]
  (let [symbols  #(->> % (into []) flatten 
                   (filter symbol?) 
                   (into #{}))]
  `(fn [m#]
     (and (map? m#)
          (<= 1 (count (l/run 1 [~@(symbols map-features)]
              (l/featurec m# ~map-features)
             ~@(for [predicate predicates]
                 `(l/project [~@(intersection 
                                  (symbols map-features) 
                                  (symbols predicate))]
                     (l/== true (~@predicate)))
                 ))))))))

(def symbols  #(->> % (into []) flatten 
                   (filter symbol?) 
                   (into #{})))

(defmacro map-features? [m features]
   `(<= 1 (count (l/run 1 [~@(symbols features)]
              (l/featurec ~m ~features)))))

(defmacro map-predicate? [m features predicate]
   `(<= 1 (count (l/run 1 [~@(symbols features)]
              (l/featurec ~m ~features)
              (l/project [~@(intersection 
                      (symbols features) 
                      (symbols predicate))]
                     (l/== true (~@predicate)))             
              ))))

(defn cammel-case-ns [text]
  (-> text 
     (clojure.string/split  #"\.")
     last 
     (clojure.string/split  #"-")
     (->>
     (map clojure.string/capitalize ))
     clojure.string/join))

(defn under-score [text]
  (-> text
      (clojure.string/split  #"\.")
        last 
     (clojure.string/replace "-" "_")))


(defmacro defrpc [name & modifying]
  (let [mod (if (= 0 (count modifying))
              false
              true)]
     `(def ~name (partial call 
                         ~mod
                         ~(keyword (clojure.string/join
                                     "."
                                    [(cammel-case-ns (str *ns*))
                                    (under-score (str name))]))))))

(defmacro defrpc-map [name features & predicates]
   (let [arg-sym (gensym "args")]
 `(def ~name `(contracts/with-constraints
    (partial call 
                       ~(keyword (clojure.string/join
                                   "."
                                  [(cammel-case-ns (str *ns*))
                                  (under-score (str name))])))))))
   ; (contracts/contract struct# "Verify map structure "
   ;       [con# arg-sym]                
   ;       [~(map-features? (first arg-sym) ~features)]))))))


