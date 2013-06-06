(defproject tcms-upload "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [necessary-evil "2.0.0"]
                 [org.clojure/tools.cli "0.2.2"]
                 [org.clojure/core.logic "0.8.3"]
                 [org.clojure/core.contracts "0.0.4"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/data.zip "0.1.1"]]
  :aot :all
  :main tcms-upload.core)
