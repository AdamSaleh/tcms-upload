(ns tcms-upload.rpc.build
  (:require 
    [tcms-upload.rpc :refer [defrpc]]))

(defrpc check-build)

(defrpc create :modifying)
