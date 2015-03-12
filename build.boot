(comment
  "Execute the following elisp to start a Clojure boot REPL."
  "This works around CIDER's assumption that lein is boss."
  ((lambda ()
           (set-variable 'cider-lein-command "boot")
           (set-variable 'cider-lein-parameters "repl")
           (cider-jack-in)))
  "First time: Put the cursor before this string and: C-xC-e"
  "After that use C-cM-j (AKA cider-jack-in) to restart boot REPL."

  "If not hacking build.boot, shell out and run: boot debug"
  "... to start up the development environment."
  "Then from Emacs visiting a Clojure buffer: M-x cider-connect"
  "Accept the cider-connect defaults with: RET RET RET."
  "Start a browser repl at the cider-repl prompt with: (start-repl)")

(set-env!
 :source-paths   #{"src"}
 :resource-paths #{"www"}
 :dependencies '[[org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [adzerk/boot-cljs "0.0-2814-1"]
                 [adzerk/boot-cljs-repl "0.1.9"]
                 [adzerk/boot-reload "0.2.4"]
                 [pandeiro/boot-http "0.6.3-SNAPSHOT"]
                 [ring "1.3.2"]
                 [tailrecursion/boot.core "2.5.1"]])

(defn import-environment-variables
  "Add value ${FOO_BAR} to (get-env) for each key :foo-bar in keywords."
  [& keywords]
  (doall (map set-env! keywords
              (map (comp #(System/getenv %)
                         clojure.string/upper-case
                         #(clojure.string/replace % "-" "_")
                         name)
                   keywords)))
  (get-env))

(import-environment-variables :google-api-key)

(require
 '[adzerk.boot-cljs             :refer [cljs]]
 '[adzerk.boot-cljs-repl        :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload           :refer [reload]]
 '[pandeiro.boot-http           :as http])

(deftask debug
  "Debug the moot client and server."
  []
  (comp
   (http/serve :handler 'moot.server/moot-app :reload true)
   (watch :verbose true)
   (cljs-repl)
   (speak)
   (reload)
   (cljs :optimizations :none
         :output-to "main.js"
         :source-map true
         :unified-mode true)))

(deftask oops
  "Say Oops"
  [x exclaim bool "Add an !"]
  (println (str "Oops" (if exclaim "!" "."))))
