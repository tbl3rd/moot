(ns moot.server
  (:import [java.io InputStreamReader PushbackReader])
  (:require [clojure.string :as s]
            [clojure.tools.reader.edn :as edn]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response]
            [tailrecursion.boot.core :as boot]))

(def the-next-id (atom 0))
(defn get-next-id [] (swap! the-next-id inc))

(def the-maps
  "The participants in a map indexed by ID."
  (atom {901 {101 {:id 101 :title "Mr Blue"      :color :blue
                   :position {:lat 42.357465 :lng -71.095194}}
              102 {:id 102 :title "Mr Green"     :color :green
                   :position {:lat 42.364251 :lng -71.110300}}
              103 {:id 103 :title "Mr LightBlue" :color :lightblue
                   :position {:lat 42.364876 :lng -71.102352}}
              104 {:id 104 :title "Mr Orange"    :color :orange
                   :position {:lat 42.369347 :lng -71.101107}}
              105 {:id 105 :title "Mr Pink"      :color :pink
                   :position {:lat 42.365257 :lng -71.087246}}
              106 {:id 106 :title "Mr Purple"    :color :purple
                   :position {:lat 42.361198 :lng -71.103983}}
              107 {:id 107 :title "Mr Red"       :color :red
                   :position {:lat 42.372083 :lng -71.082062}}
              108 {:id 108 :title "Mr Yellow"    :color :yellow
                   :position {:lat 42.366465 :lng -71.095194}}}}))

(def marker-icon-colors
  "The colors available for map marker (x.png and x-dot.png) icons."
  (let [colors [:blue :green :red :yellow :orange :pink :purple]
        dotted (map #(keyword (str (name %) "-dot")) colors)]
    (reduce conj colors dotted)))

(defn get-guy-color-for-map
  [map-id guy-id]
  (let [state @the-maps]
    (if-let [result (get-in state [map-id guy-id :color])]
      result
      (let [used (set (map :color (vals (get state map-id))))
            color (first (remove used marker-icon-colors))]
        (or color (first marker-icon-colors))))))

(defn update-guy
  "Update guy in map with MAP-ID."
  [map-id guy]
  (let [guy-id (or (:id guy) (get-next-id))
        color (get-guy-color-for-map map-id guy-id)
        title (or (:title guy) (str "Mr " (s/capitalize (name color))))
        position (:position guy)
        new-guy {:id guy-id
                 :title title
                 :color color
                 :position position}]
    (let [state (swap! the-maps
                       (fn [state]
                         (update-in state [map-id] assoc guy-id new-guy)))]
      (get-in state [map-id guy-id]))))

(defn body-edn
  "The body of REQUEST parsed as EDN."
  [request]
  (edn/read-string
   (slurp
    (java.io.PushbackReader.
     (java.io.InputStreamReader.
      (:body request))))))

(defn wrap-dump-uri
  "If REQUEST is for URI dump the request and response tagged with MSG."
  [handler uri msg]
  (fn [request]
    (let [response (handler request)]
      (if (.startsWith (:uri request) uri)
        (println (pr-str {:msg msg :request request :response response})))
      response)))

(comment
  "A URI looks like this: /update/MAP-ID/ with the user's data in the POST."
  "Example: /update/909/"
  "POST" {:id 105 :title "Abe" :position {:lat 42.365257 :lng -71.087246}}
  "If there is no MAP-ID or if the MAP-ID is not found, make a new map."
  "Update" the-maps "with the user's new location and name."
  "If no" :id "entry in user's update info, allocate a color to the user,"
  "and add the user's information to the map for MAP-ID."
  "Result is:" {MAP-ID (get-in @the-maps MAP-ID)})

(defmacro try-or-nil
  "Value of BODY or nil if an exception is thrown."
  [& body]
  `(try (do ~@body) (catch Exception e# (println e#))))

(defn map-id-from-uri
  "Return nil or the map ID parsed from URI."
  [uri]
  (let [re #"^/update/(.+)/$"
        [update map-id] (re-find re (s/lower-case uri))]
    (try-or-nil
     (if (and update map-id)
       (let [id (edn/read-string map-id)]
         (if (and id (> id 0)) id))))))

(defn handle-request
  "Return a response for REQUEST."
  [request]
  (let [response {:headers {"content-type" "text/plain"}}
        fail (assoc response :status 400 :body "Bad map request.")
        post? (= :post (:request-method request))
        map-id (map-id-from-uri (:uri request))
        succeed (assoc response :status 400)
        body (fn [] {:map-id map-id
                     :all (set (vals (get @the-maps map-id)))})]
    (cond (and post? map-id)
          (let [you (update-guy map-id (body-edn request))]
            (doto (assoc succeed :body (pr-str (assoc (body) :you you))) println))
          map-id
          (if-let [cv (get-in request [:cookies :value])]
            (let [you (select-keys cv [:id :title :color])
                  you (update-guy map-id you)]
              (assoc succeed :body (pr-str (assoc (body) :you you))))
            fail)
          :else fail)))

(def moot-app
  "The server callback entry point."
  (-> handle-request
      (wrap-dump-uri "/update/" :first)
      wrap-cookies
      wrap-params
      (wrap-file "target" {:index-files? true})
      wrap-file-info                    ; works!
      (wrap-dump-uri "/update/" :last)))

(println [:RELOADED 'server])
