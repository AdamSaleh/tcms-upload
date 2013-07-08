(ns tcms-upload.rpc.test-case
  (:refer-clojure :exclude [filter])
  (:require 
    [tcms-upload.rpc :refer [defrpc]]))

(defrpc create :modifying)
(defrpc link-plan :modifying)
(defrpc filter)
