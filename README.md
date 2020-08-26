KeyFinderDroid
===========
This tool helps find hard-coded data that is used to create encryption keys. It uses the static analysis framework Soot and the extensions created for Flowdroid. It looks for specified constructors of secret key objects and attempts to track the data that was used as the key information.

Check out the wiki for details on how it works!

Dependencies
============
Soot and FlowDroid are used in KFD. You can grab the source code or nightly build [here](https://github.com/secure-software-engineering/soot-infoflow-android/wiki). It is recommended to use the nightly builds. Make sure to download the libraries for logging as they are used to display some debugging messages. The list of jars downloaded should look like:

```
axml-2.0.jar
slf4j-api-1.7.5.jar
slf4j-simple-1.7.5.jar
soot-infoflow-android.jar
soot-infoflow.jar
soot-trunk.jar
```

Graphviz is used to visualize the output of KFD. You can visit their download page [here](http://www.graphviz.org/Download..php) or simply 

Ubuntu
```
sudo apt-get install graphviz
```

Building and Running
====================
Before you build KFD, please edit ```build.gradle``` and set the ```flowdroid_src``` point to the location of the jars you obtained. The default value is ```./libs```

To build KFD
```
./gradlew build
```

To run KFD

```
./kdf -a <path to apk> [-o <output folder> -s <path to Android SDK>]
```

The output folder defaults to "./kdfOutput". The default path to the Android SDK is "./res/android.jar".

Remember to set the Java heap space to an acceptable amount. Large applications such as internet browsing applications required around 8G. You can do so by Uncommenting the jvmArgs line in the build.gradle file.

The default output folder is ```./kfdOutput```. The output folder will contain a dot file for each method specified in ```KeyConstructors.txt```. You can use the ```plot_dots.sh``` to convert each dot file into a pdf.

```
./plot_dots.sh kfdOutput
```

Examples
========
There are example APKs available that succinctly demonstrate some of the cases where KFD can find encryptions, and cases where it cannot find them.

For a real world example, KFD was used to analyze the Baidu browser and it found hard coded encryptions. This result is consistent with the report published by the Citizen Lab. See Part 1 of the [report](https://citizenlab.org/2016/02/privacy-security-issues-baidu-browser/). Below is a subset of the result.

![Alt text](/res/doc/baidu_subset_example.png)

Note that in this example, the key was ```"h9YLQoINGWyOBYYk"```. It was stored in an array and it is accessed by the index ```i = 1 - 1```. This operation seems like a mistake, but that is how the code was converted to Jimple.
