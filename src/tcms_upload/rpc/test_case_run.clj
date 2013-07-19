(ns tcms-upload.rpc.test-case-run
  (:refer-clojure :exclude [filter])
  (:require 
    [tcms-upload.rpc :refer [defrpc]]))

(defrpc create :modifying)
(defrpc update :modifying)
(defrpc filter)
(defrpc attach-log :modifying)
(defrpc attach-bug :modifying)
(defrpc add-comment :modifying)
