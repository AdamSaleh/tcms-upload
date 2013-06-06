(ns rpc.test-case-run
  (:require 
    [tcms-upload.tcms-rpc :refer [defrpc defrpc-map]]))

(defrpc create :modifying)

;  {:build ?build :run ?run :case ?case :case_run_status ?status})