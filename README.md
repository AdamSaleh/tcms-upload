# tcms-upload

A simple command-line app for uploading test-ng xml results to tcms test runs.

Requires for the test cases in xml and tcms to share an uuid string, namely, it links test-ng test-method with tcms test-case that has alias equal to test-methods description. It then creates test-run, with test-case runs, that link test-method status with test-case-run case-run-status. 

## Usage

Your command should look simirraliry to this:

```
java -jar tcms-upload-0.1.12-standalone.jar --username asaleh --password $PASSWORD 
--plan 9032 --xml-result testng-results.xml --manager-login jweiss --summary "tcms demo showcase run" --build-name 2013-07-20-build
```


If something does not work and you see no reasonable error message in log, please file an issue on github, that includes the xml, the command and the log.

As of now, we don't support any kind of dry run, so if you will need to erase some sort of a bad upload, you can use the "tcms-uploader" tag to help you narrow the search.

## Params

* --username : your kerberos username
* --password : your kerberos password
* --rpc-url : set to default RedHat TCMS server, so you dont need to specify it ... if you think you do, please contact me, I would like to see the mad man who uses tcms outside of RedHat :-)
*  xml-result : test-ng result xml, that contains special test case UUIDs in each test-method description that you like to upload. Example of such xml-file is the testng-results.xml in top level of the repo, or smaller, more readable testng-results-min.xml.

```
	<test-method status="PASS" signature="test_errataList()[pri:0, instance:com.redhat.qe.katello.tests.cli.ErrataTests@62bbb074]" name="test_errataList" duration-ms="1043" started-at="2013-05-23T23:54:37Z" description="8782a6e0-f41a-48d5-8599-bfe7f24078f6" finished-at="2013-05-23T23:54:38Z" />
```

* --plan: id of the test plan accordint to which you run your tests. If you have access to Red Hat Tcms, you can look at plan 9032, katello-cli-automation-runs. You can see, that test case 269819, Katello cli `errata list` has in the field alias the same uuid as is in the description of the test method. This links the case from xml to case int test plan and helps in creating test-case-run

* --build-name : name of the build, IT WILL CREATE IT IF IT DOES NOT EXIST. You can verify that you enterd already existing build-name, if you do a dry-run and you will see log similar to

```
Jun 13, 2013 12:56:48 PM clojure.tools.logging$eval1$fn__7 invoke
INFO: Calling: :Build.check_build  with  [new-build 313]
Jun 13, 2013 12:56:49 PM clojure.tools.logging$eval1$fn__7 invoke
INFO: Resolved params: {:build 3576}
```

If build does not exist, respective lines would look like this:
```
INFO: Calling: :Build.check_build  with  [not-yet-created-build 313]
Jun 13, 2013 1:47:16 PM clojure.tools.logging$eval1$fn__7 invoke
INFO: Calling: :Build.check_build  with  [unspecified 313]
```
Build "unspecified" then serves as a placeholder build for the rest of the dry run, and in real run, the "not-yet-created-build" will be created and used instead.

* --manager-login : each test run in tcms has manager assigned at creation, you need to specify his login. 

* --summary : test summary of the test run

## Questions, that you might ask:

* Do I allways need to create new run?
   * no, you can specify old run id with --run option. By default it shall overwrite all the previous case-results.
* Why all this work with uuid, alias and description? Couldnt you just use test-case id from tcms?
   * Because our team has one test-suite for several products, we need to clone and modify our test-plans from time to time. While cloned test cases would be the same, their id would differ. That is why we create uuid in alias.
* What happens if I have test-cases with the same alias in tcms?
   * All of them will recieve the same result from xml.
* What happens if I have test-cases with the same description in xml?
   * I have no idea. Most probably, only the first test-method result will be uploaded, and you will see lot of "can not create duplicate test case run" errors

## How to compile?

It is a standard lein project.

1) Download and install leiningen
2) git clone git@github.com:AdamSaleh/tcms-upload.git
3) cd tcms-upload
4) lein deps
6) lein uberjar

Now you have a fresh jar to use.
 
## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
