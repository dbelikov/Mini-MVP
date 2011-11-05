
Introduction

The main goal for Mini-MVP (Model View Presenter) library is to clearly separate your models from business logic and UI representation while maintaining their relationships in a concise and well-defined manner.

Let's say you have some data model that you'd like to use.
(def my-model (mvp/create { :type Text :hint "Enter your age" :value "1234" :error "Value too big to be true" }))

You define a control that will render the text from the model. I'm using jQuery here, the sample also demonstrates Closure.

;; Defines a subscriber that will update control's value if the model changes.
(mvp/add-reader text-model [:text] #(.val (jq "#text2") (:new-value %)))

;; In a similar way, we also render the error message
(mvp/add-reader my-model [:error] #(.html (jq "#error-message") (:new-value %)))

;; let's update the error message if the age exceeds 120
(mvp/add-writer my-model [:value]
                  #(if (> (:new-value %) 120)
                     (mvp/assoc-value my-model [:error] "Value too big to be true")
                     (mvp/assoc-value my-model [:error] "")))

;; finally, update the model when the age is changed
(.change (jq "#input-control") #(mvp/assoc-value my-model [:value] (. (jq "#input-control") (val))))

Now, if you enter any age less than 120, the error message is cleared. Enter more than 120 and it'll show up again.




API

create <some clojure data model> <optional initial version> <optional function to suggest the next version given the current>
       Creates a new MVP model

create-ref <existing MVP mode> <path in the model>
       Creates a model that references a subpath in the parent model

get-version <MVP model>
       Answers the current version of the model. Each change causes version change.
       Assigning equal values is a no-op that doesn't cause version change.

get-value <MVP model> [path] <optional default value>
       Similar to clojure.core/get-in, gets the value in the model under the specified path.
       Empty path fetches the root of the model.

update-value <MVP model> [path] <function to apply> <additional arguments to the function ...>
       Similar to clojure.core/update-in, updates the model's value under the specified path. The specified function is invoked to get the new value.
       Producing equal value will be considered a no-op.

assoc-value <MVP model> [path] <new-value>
       Similar to clojure.core/assoc-in, associates a new value with the sub-model at the specified path.
       Producing equal value will be considered a no-op.

add-writer <MVP model> [path] <subscriber function>
       Adds a 1st phase function that will be invoked if the value under the specified path changes.
       The function is not invoked if the change(s) didn't cause value change.
       I.e. given a model { :first "CA" :second "NY" } which is being updated to { :first "WA" :second "NY" }, 
       the subscriber for :second isn't invoked. The subscribers for [] and [:first] are to be called.

add-reader <MVP model> [path] <subscriber function>
       Exactly the same as add-writer, but these folks are invoked after all writers. 
       The idea is to avoid unnecessary UI rendering/flickering while validators/statistics get updated.

clear <MVP model> [path]
       Eliminates all subscribers under the specified path.

delay-events <MVP model> <function>
       All subscriber calls are delayed until after the function completes.
       Only subscribers which guarded value has changed will be called.
       I.e. given a subscriber for path=[1] in a model ["a" "b" "c"], three subsequent changes within (delay-events ...)
       ["a" "b" "c"] -> ["b" "c" "d"] -> ["e" "f" "g"] -> ["h" "b" "e"] won't trigger the subscriber.

version <MVP model>
       Returns the current version of the model. By default, a new unique identifier is generated after each actual change.
       You can define the initial version and a function to generate new versions:
       (mvp/create {...my data...} 12 inc)  ;; will start with version=12, which is incremented upon each change




License

Same as Clojure/ClojureScript.


