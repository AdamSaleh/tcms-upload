(ns tcms-upload.rpc.user
  (:refer-clojure :exclude [filter])
  (:require 
    [tcms-upload.rpc :refer [defrpc]]))

(defrpc filter)
