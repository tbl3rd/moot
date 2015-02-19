(ns server
  (:require [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]))

(defn wrap-println
  [handler]
  (fn [request]
    (let [response (handler request)]
      (println [:request request])
      (println [:response response])
      response)))

(defn handler
  [request]
  (println [:server/handler {:request request}]))

(def moot-app
  "The server callback entry point."
  (-> handler
      ;; (wrap-resource "target")
      (wrap-file "target" {:index-files? true})
      wrap-file-info                    ; works!
      ;; wrap-content-type                 ; does not work
      ;; wrap-not-modified                 ; does not work
      wrap-println))
