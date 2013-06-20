(defproject tcms-upload "0.1.7"
  :description "A simple command-line app for uploading test-ng xml results to tcms test runs."
  :url "https://github.com/AdamSaleh/tcms-upload"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [necessary-evil "2.0.0"]
                 [slingshot "0.10.3"]
                 [org.clojure/tools.namespace "0.2.3"]
                 [org.clojure/tools.cli "0.2.2"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/data.zip "0.1.1"]]
  :main tcms-upload.core
  )
