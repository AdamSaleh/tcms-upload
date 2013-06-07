# tcms-upload

A simple command-line app for uploading test-ng xml results to tcms test runs.

Requires for the test cases in xml and tcms to share an uuid string, namely, it links test-ng test-method with tcms test-case that has alias equal to test-methods description. It then creates test-run, with test-case runs, that link test-method status with test-case-run case-run-status. 

## Usage

 java -jar tcms-upload-0.1.0-SNAPSHOT-standalone.jar --dry-run --username asalehh --password #Nitrate1 --xml-result /home/asaleh/clean-room/tcms-upload/testng-results.xml --plan 9023 --build-name unspecified --manager-login asaleh --summary test23

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
