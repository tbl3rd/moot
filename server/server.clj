(ns server
  (:require [clojure.string :as s]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.resource :refer [wrap-resource]]
            [tailrecursion.boot.core :as boot]))

(defn wrap-println
  [handler]
  (fn [request]
    (let [response (handler request)]
      (println (pr-str {:request request :response response}))
      response)))

(defn handler
  [request]
  (let [method (:request-method request)
        uri (s/lower-case (:uri request))]
    (cond (and (= method :post) (= uri "/update"))
          (println [:server/handler :update]))))

(def moot-app
  "The server callback entry point."
  (-> handler
      (wrap-reload {:dirs ["server"]})
      (wrap-file "target" {:index-files? true})
      wrap-file-info                    ; works!
      wrap-println))

(println {:dirs (into [] (boot/get-env :directories))})
(println [:RELOADED 'server])
