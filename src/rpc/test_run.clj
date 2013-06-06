(ns rpc.test-run 
  (:require 
    [tcms-upload.tcms-rpc :refer [defrpc defrpc-map]]))

(defrpc create :modifying)
;  {:plan ?plan :build ?build :manager ?manager 
;   :summary ?summary :product ?product :product_version ?product_version})
