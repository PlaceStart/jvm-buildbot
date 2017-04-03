(defproject placestart "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "https://github.com/PlaceStart/jvm-buildbot"
  :license {:name "MIT License" :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [clj-http "2.3.0"]
                 [seesaw "1.4.5"]
                 [net.mikera/imagez "0.12.0"]
                 [cheshire "5.7.0"]]
  :main ^:skip-aot placestart.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
