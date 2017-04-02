(ns placestart.core
  (:gen-class)
  (:require [clj-http.client :as client]
            [mikera.image.core :as img]
            [mikera.image.colours :as color])
  (:use [clojure.java.io :only (as-url)]))

; Utilities for the Reddit API
(def api-base "https://www.reddit.com/api/")
(defn api [endpoint] (str api-base endpoint))

; URLs for later use
(def template-url (as-url "https://github.com/PlaceStart/placestart/raw/master/target.png"))
(def bitmap-url (api "place/board-bitmap"))

(defn retry-get
  "Retry a GET request up to a limited number of times"
  ([url max-retries opts]
   (loop [retries max-retries]
     (let [resp (client/get url opts)]
       (if (= (:status resp) 200)
         resp
         (if (zero? retries) resp
           (do
             (Thread/sleep 1000)
             (recur (dec retries))))))))
  ([url retries] (retry-get retries {}))
  ([url] (retry-get url 10)))

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
(def color-map-inv (clojure.set/map-invert color-map))

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
(def code-inv-map (clojure.set/map-invert code-map))

; rgb-from-components is a macro, so this allows use in map
(defn pack-rgb [[r g b]] (color/rgb-from-components r g b))

(defn get-pixmap
  "Get the pixels in an image as a mapping of coordinates to pixel colors. Omit
  pixels with the dummy value."
  [image]
  (into {} 
        (filter
          (fn [[[x y] c]] (not (= c :dummy)))
          (for [x (range (img/width image))
                y (range (img/height image))] ; get all pixels
            [[x y] (get color-map (color/components-rgb (img/get-pixel image x y)))]))))

(defn pixmap-to-image
  "Convert a pixmap into an image"
  [pixmap]
  (reduce (fn [i [[x y] c]] (do (img/set-pixel i x y (pack-rgb (get color-map-inv c))) i))
          (img/new-image 1000 1000)
          pixmap))

(defn get-template "Get an updated copy of the PlaceStart template" []
  (let [image (img/load-image template-url)
        pixels (get-pixmap image)
        base-y (- 1000 (img/height image))]
    ; shift the image down to the bottom left corner
    (into {} (map (fn [[[x y] v]] [[x (+ base-y y)] v]) pixels))))

(defn get-board
  "Get the current state of the board"
  []
  (let [response (retry-get bitmap-url 10 {:as :byte-array})
        board-content (drop 4 (:body response)) ; ignore timestamp
        bit-packed (apply concat (map (fn [n]
                                        [(bit-and 0xf (unsigned-bit-shift-right n 4))
                                         (bit-and 0xf n)])
                                      board-content))
        colorized (map #(get code-inv-map %) bit-packed)]
    (zipmap (for [y (range 1000)
                  x (range 1000)] [x y])
            colorized)))

(defn find-errors
  "Find errors between the given board and template"
  [board template]
  (into {} (filter some?
   (merge-with (fn [l r] ; return colors that need changing or nil on match 
                 (if-not (= l r) (do (println (str l r)) r) nil))
               (select-keys board (keys template)) template))))

(defn modify-pixel
  "Send a request to the Reddit API to set the color of a pixel"
  [[x y] color]
  )

(defn -main
  [& args]
  (doall
    )
  )
