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
 :resource-paths #{"html"}
 :dependencies '[[adzerk/boot-cljs      "0.0-2411-3" :scope "test"]
                 [adzerk/boot-cljs-repl "0.1.7"      :scope "test"]
                 [adzerk/boot-reload    "0.2.0"      :scope "test"]
                 [pandeiro/boot-http    "0.3.0"      :scope "test"]])

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
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
 '[pandeiro.http         :refer [serve]])

(deftask debug
  []
  (comp (serve :dir "target")
        (watch)
        (speak)
        (reload)
        (cljs-repl)
        (cljs :optimizations :none
              :source-map true
              :unified-mode true)))
