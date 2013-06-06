(ns tcms-upload.rpc
    (:require [clojure.tools.logging :as log]
              [necessary-evil.core :as rpc]
              [slingshot.slingshot :refer [throw+]]
              [clojure.set :refer [intersection]]
              [necessary-evil.fault :refer [fault?]]))

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