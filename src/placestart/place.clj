(ns placestart.place
  (:gen-class)
  (:require [clj-http.client :as client]
            [mikera.image.core :as img]
            [mikera.image.colours :as color])
  (:use [clojure.java.io :only (as-url)]))

; Utilities for the Reddit API
(def api-base "https://www.reddit.com/api/")
(defn api [endpoint & more] (apply str api-base endpoint more))
(def user-agent "PlaceStartJVM")

; URLs for later use
(def template-url (as-url "https://github.com/PlaceStart/target-image/raw/master/target.png"))
(def bitmap-url (api "place/board-bitmap"))

(def next-allowed-request (atom (java.time.Instant/now)))

(defn polite-op
  "Wait for the next API timeout then send a request"
  [f url opts]
  (loop []
    (if (.isAfter (java.time.Instant/now) @next-allowed-request)
      (do 
        (reset! next-allowed-request (.plusSeconds (java.time.Instant/now) 2.1))
        (f url opts))
      (do
        (Thread/sleep 100)
        (recur)))))

(defn retry-get
  "Retry a GET request up to a limited number of times"
  ([url max-retries opts]
   (loop [retries max-retries]
     (let [resp (polite-op client/get url (assoc opts "User-Agent" user-agent))]
       (if (= (:status resp) 200)
         (do (Thread/sleep 2000) resp)
         (if (zero? retries) resp
           (do
             (Thread/sleep 2000)
             (recur (dec retries))))))))
  ([url retries] (retry-get retries {}))
  ([url] (retry-get url 10)))

(defn polite-post
  [url opts]
  (polite-op client/post url opts))

(defn login
  "Log into the Reddit API with a given username and password, and return the
  resulting map of :cookies and :modhash."
  [username password]
  (let [resp (polite-post (api "login/" username)
                          {:form-params {:user username
                                         :passwd password
                                         :api_type "json"}
                           :headers {"User-Agent" user-agent}
                           :as :json})
        body (get-in resp [:body :json :data])
        cookies (:cookies resp)]
    {:modhash (:modhash body)
     :reddit-session (:cookie body)}))

(defn auth-post
  "Run a POST request as an authenticated user"
  ([auth url options]
   (let [headers (merge (:headers options) {"User-Agent" user-agent
                                            "Cookie" (str "reddit_session=" (:reddit-session auth))
                                            "x-modhash" (:modhash auth)})]
     (polite-post url (assoc options :headers headers)))))

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
        bit-packed (mapcat (fn [n]
                             [(bit-and 0xf (unsigned-bit-shift-right n 4))
                              (bit-and 0xf n)])
                           board-content)
        colorized (map #(get code-inv-map %) bit-packed)]
    (zipmap (for [y (range 1000)
                  x (range 1000)] [x y])
            colorized)))

(defn find-errors
  "Find errors between the given board and template"
  [board template]
  (into {} (filter #(some? (second %)) ; return colors that need changing
                   (merge-with (fn [l r] (if-not (= l r) r nil))
                               (select-keys board (keys template)) template))))

(defn read-pixel
  "Read a pixel by sending a request to the Reddit API"
  [[x y]]
  (let [resp (retry-get (api "place/pixel.json?x=" x "&y=" y) 10 {:as :json})]
    (get code-inv-map (get-in resp [:body :color]))))

(defn modify-pixel
  "Send a request to the Reddit API to set the color of a pixel"
  [auth [x y] color]
  (merge
    (:body (auth-post auth (api "place/draw.json")
                      {:form-params {:x (str x) :y (str y)
                                     :color (get code-map color)}
                       :as :json}))
    {:pos [x y] :color color}))

(defn choose-error
  "Given all the errors, choose one to fix. Returns a [pos color] pair."
  [errs]
  (first (sort-by #(get-in % [0 0]) errs)))

(def cur-board (atom (get-board)))
(def cur-template (atom (get-template)))

(defn update-template
  "Update the current template"
  []
  (reset! cur-template (get-template)))

(defn update-board
  "Update the current global board information"
  []
  (reset! cur-board (get-board)))

(defn fix-one
  "Locate an error pixel, then fix it. This uses the global template, but checks
  for a new board every time."
  [auth]
  (let [errors (find-errors @cur-board @cur-template)
        [pos color] (choose-error errors)
        wrong-color (get @cur-board pos)]
    (if (= (read-pixel pos) color)
      (do
        (Thread/sleep 2000)
        (update-board)
        (fix-one auth)) ; just try again - our board was out of date
      (assoc (modify-pixel auth pos color) ; change the pixel
             :old-color wrong-color))))

