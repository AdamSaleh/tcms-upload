(ns tcms-upload.rpc.test-case-run
  (:require 
    [tcms-upload.rpc :refer [defrpc]]))

(defrpc create :modifying)
(defrpc add-comment :modifying)
