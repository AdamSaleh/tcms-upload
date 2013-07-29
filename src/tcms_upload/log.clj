(ns tcms-upload.log
    (:require 
              [slingshot.slingshot :refer [throw+]]
      [clojure.pprint :refer [pprint]]
              [clojure.set :refer [intersection]]
  ))


(defn stamp []
  (str (java.util.Date.)))

(defn log [message]
  (spit "tcms-upload.log" (with-out-str
                           (println (stamp)) (pprint message)) :append true))

(defn side-log [message]
  (do 
    (log message)
    message))
