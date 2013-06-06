(ns tcms-upload.rpc.test-plan
  (:require 
    [tcms-upload.rpc :refer [defrpc]]))

(defrpc get-test-cases)
(defrpc get)
