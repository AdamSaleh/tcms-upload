(ns tcms-upload.rpc.test-plan
  (:refer-clojure :exclude [get]) (:require 
      [tcms-upload.rpc :refer [defrpc]]))

(defrpc get-test-cases)
(defrpc get)
