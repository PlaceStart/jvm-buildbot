(ns placestart.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [mikera.image.core :as img])
  (:use [clojure.java.io :only (as-url)]))

; Utilities for the Reddit API
(def api-base "https://www.reddit.com/api/")
(defn api [endpoint] (as-url (str api-base endpoint)))

; URLs for later use
(def template-url (as-url "https://github.com/PlaceStart/placestart/raw/master/target.png"))
(def bitmap-url (api "place/board-bitmap"))

(def color-map {[255 255 255] :white
                [228 228 228] :lightgray
                [136 136 136] :darkgray
                [34 34 34]    :black
                [255 167 209] :lightpink
                [229 0 0]     :red
                [229 149 0]   :orange
                [160 106 66]  :brown
                [229 217 0]   :yellow
                [148 224 68]  :lightgreen
                [2 190 1]     :green
                [0 211 221]   :cyan
                [0 131 199]   :grayblue
                [0 0 234]     :blue
                [207 110 228] :pink
                [130 0 128]   :purple
                [54 199 57]   :dummy})

(def code-map {:white      0
               :lightgray  1
               :darkgray   2
               :black      3
               :lightpink  4
               :red        5
               :orange     6
               :brown      7
               :yellow     8
               :lightgreen 9
               :green      10
               :cyan       11
               :grayblue   12
               :blue       13
               :pink       14
               :purple     15
               :dummy      16})

(defn split-rgb
  "Split a color integer into rgb components"
  [color]
  [(bit-and 0xff (unsigned-bit-shift-right color 16))
   (bit-and 0xff (unsigned-bit-shift-right color 8))
   (bit-and 0xff color)])

(defn get-pixmap
  "Get the pixels in an image as a mapping of coordinates to pixel colors. Omit
  pixels with the dummy value."
  [image]
  (into {} 
        (filter
          (fn [[[x y] c]] (not (= c :dummy)))
          (for [x (range (img/width image))
                y (range (img/height image))] ; get all pixels
            [[x y] (get color-map (split-rgb (img/get-pixel image x y)))]))))

(defn get-template "Get an updated copy of the PlaceStart template" []
  (get-pixmap (img/load-image template-url)))

(defn get-board
  "Get the current state of the board"
  []
  )

(defn find-errors
  "Find errors between the given board and template"
  [board template]
  )

(defn modify-pixel
  "Send a request to the Reddit API to set the color of a pixel"
  [[x y] color]
  )

(defn -main
  [& args]
  (doall
    )
  )
