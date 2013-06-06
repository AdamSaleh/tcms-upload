(ns rpc.test-plan
  (:require 
    [tcms-upload.tcms-rpc :refer [defrpc defrpc-map]]))

(defrpc get-test-cases)
(defrpc get)
