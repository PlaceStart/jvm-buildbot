(ns placestart.core
  (:gen-class)
  (:use seesaw.core placestart.place))

(defn- build-root-frame
  "Build the root frame of the UI"
  (frame
    :title "PlaceStartJVM Automated Maintenance"
    :size [800 :by 600]
    )
  )

(defn init-ui
  "Initialize the UI"
  []
  )

(defn -main
  [& args]
  (do
    )
  )
