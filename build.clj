(comment To compile start a clojrue repl that has cljs classpathed and run this)
(require '[cljs.closure :as cljsc])

(cljsc/build "./src" {:output-dir "./out" :output-to "./minimvp.js"})

