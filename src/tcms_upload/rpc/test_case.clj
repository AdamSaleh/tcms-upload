(ns tcms-upload.rpc.test-case
  (:require 
    [tcms-upload.rpc :refer [defrpc]]))

(defrpc create :modifying)
(defrpc link-plan :modifying)
(defrpc filter)
