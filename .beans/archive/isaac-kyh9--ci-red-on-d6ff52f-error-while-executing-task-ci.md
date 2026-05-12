---
# isaac-kyh9
title: 'CI red on d6ff52f: Error while executing task: ci'
status: completed
type: bug
priority: high
created_at: 2026-05-12T04:38:33Z
updated_at: 2026-05-12T17:22:45Z
---

## Description

CI verification failed on push to main.

- Commit: d6ff52f9c488f752755318fab3a0a327f065d045
- Short SHA: d6ff52f
- Branch: main
- Repository: slagyr/isaac
- GitHub actor: slagyr
- Commit author: Micah <micahmartin@gmail.com>
- Run: https://github.com/slagyr/isaac/actions/runs/25713680396

Summary:
Error while executing task: ci

Failure excerpt:
```text
at sci.impl.interpreter$eval_string_STAR_.invokeStatic(interpreter.cljc:72)
	at sci.core$eval_string_STAR_.invokeStatic(core.cljc:281)
	at babashka.main$load_file_STAR_.invokeStatic(main.clj:326)
	at babashka.main$load_file_STAR_.invoke(main.clj:322)
	at sci.lang.Var.invoke(lang.cljc:182)
	at sci.impl.analyzer$return_call$reify__4860.eval(analyzer.cljc:1536)
	at sci.impl.fns$fun$arity_1__1341.invoke(fns.cljc:124)
	at clojure.core$comp$fn__5897.invoke(core.clj:2586)
	at clojure.core$run_BANG_$fn__8926.invoke(core.clj:7907)
	at clojure.core.protocols$fn__8279.invokeStatic(protocols.clj:167)
	at clojure.core.protocols$fn__8279.invoke(protocols.clj:123)
	at clojure.core.protocols$fn__8233$G__8228__8242.invoke(protocols.clj:19)
	at clojure.core.protocols$seq_reduce.invokeStatic(protocols.clj:31)
	at clojure.core.protocols$fn__8266.invokeStatic(protocols.clj:74)
	at clojure.core.protocols$fn__8266.invoke(protocols.clj:74)
	at clojure.core.protocols$fn__8207$G__8202__8220.invoke(protocols.clj:13)
	at clojure.core$reduce.invokeStatic(core.clj:6965)
	at clojure.core$run_BANG_.invokeStatic(core.clj:7902)
	at clojure.core$run_BANG_.invoke(core.clj:7902)
	at sci.lang.Var.invoke(lang.cljc:184)
	at sci.impl.analyzer$return_call$reify__4922.eval(analyzer.cljc:1536)
	at sci.impl.fns$fun$arity_1__1341.invoke(fns.cljc:124)
	at sci.lang.Var.invoke(lang.cljc:182)
	at sci.impl.analyzer$return_call$reify__4860.eval(analyzer.cljc:1536)
	at sci.impl.evaluator$eval_try.invokeStatic(evaluator.cljc:83)
	at sci.impl.analyzer$analyze_try$reify__4656.eval(analyzer.cljc:933)
	at sci.impl.analyzer$return_do$reify__4313.eval(analyzer.cljc:80)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4588.eval(analyzer.cljc:618)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4592.eval(analyzer.cljc:636)
	at sci.impl.analyzer$return_do$reify__4315.eval(analyzer.cljc:85)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4596.eval(analyzer.cljc:670)
	at sci.impl.fns$fun$arity_3__1352.invoke(fns.cljc:126)
	at clojure.lang.MultiFn.invoke(MultiFn.java:239)
	at sci.lang.Var.invoke(lang.cljc:186)
	at sci.impl.analyzer$return_call$reify__4926.eval(analyzer.cljc:1536)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4592.eval(analyzer.cljc:634)
	at sci.impl.analyzer$return_do$reify__4313.eval(analyzer.cljc:80)
	at sci.impl.evaluator$eval_try.invokeStatic(evaluator.cljc:83)
	at sci.impl.analyzer$analyze_try$reify__4656.eval(analyzer.cljc:933)
	at sci.impl.evaluator$eval_try.invokeStatic(evaluator.cljc:83)
	at sci.impl.analyzer$analyze_try$reify__4656.eval(analyzer.cljc:933)
	at sci.impl.analyzer$return_do$reify__4313.eval(analyzer.cljc:80)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4588.eval(analyzer.cljc:618)
	at sci.impl.analyzer$return_do$reify__4313.eval(analyzer.cljc:80)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4596.eval(analyzer.cljc:670)
	at sci.impl.evaluator$eval_try.invokeStatic(evaluator.cljc:83)
	at sci.impl.analyzer$analyze_try$reify__4656.eval(analyzer.cljc:933)
	at sci.impl.analyzer$return_do$reify__4313.eval(analyzer.cljc:80)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4588.eval(analyzer.cljc:618)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4590.eval(analyzer.cljc:625)
	at sci.impl.fns$fun$arity_0__1337.invoke(fns.cljc:123)
	at sci.impl.analyzer$return_call$reify__4834.eval(analyzer.cljc:1536)
	at sci.impl.evaluator$eval_try.invokeStatic(evaluator.cljc:83)
	at sci.impl.analyzer$analyze_try$reify__4656.eval(analyzer.cljc:933)
	at sci.impl.analyzer$return_do$reify__4313.eval(analyzer.cljc:80)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4588.eval(analyzer.cljc:618)
	at sci.impl.fns$fun$arity_2__1346.invoke(fns.cljc:125)
	at sci.lang.Var.invoke(lang.cljc:184)
	at sci.impl.analyzer$return_call$reify__4922.eval(analyzer.cljc:1536)
	at sci.impl.fns$fun$arity_1__1341.invoke(fns.cljc:124)
	at sci.lang.Var.invoke(lang.cljc:182)
	at sci.impl.analyzer$return_call$reify__4860.eval(analyzer.cljc:1536)
	at sci.impl.analyzer$return_or$reify__4326.eval(analyzer.cljc:126)
	at sci.impl.analyzer$return_if$reify__4624.eval(analyzer.cljc:857)
	at sci.impl.analyzer$return_if$reify__4624.eval(analyzer.cljc:858)
	at sci.impl.analyzer$return_if$reify__4624.eval(analyzer.cljc:858)
	at sci.impl.analyzer$return_if$reify__4624.eval(analyzer.cljc:858)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4590.eval(analyzer.cljc:625)
	at sci.impl.fns$fun$arity_0__1043.doInvoke(fns.cljc:100)
	at clojure.lang.RestFn.applyTo(RestFn.java:140)
	at clojure.core$apply.invokeStatic(core.clj:667)
	at clojure.core$apply.invoke(core.clj:662)
	at sci.lang.Var.invoke(lang.cljc:184)
	at sci.impl.analyzer$return_call$reify__4922.eval(analyzer.cljc:1536)
	at sci.impl.analyzer$return_call$reify__4862.eval(analyzer.cljc:1536)
	at sci.impl.fns$fun$arity_0__1043.doInvoke(fns.cljc:100)
	at clojure.lang.RestFn.applyTo(RestFn.java:140)
	at clojure.core$apply.invokeStatic(core.clj:667)
	at clojure.core$apply.invoke(core.clj:662)
	at sci.lang.Var.invoke(lang.cljc:184)
	at sci.impl.analyzer$return_call$reify__4922.eval(analyzer.cljc:1536)
	at sci.impl.analyzer$return_if$reify__4624.eval(analyzer.cljc:858)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4590.eval(analyzer.cljc:625)
	at sci.impl.analyzer$return_do$reify__4313.eval(analyzer.cljc:80)
	at sci.impl.evaluator$eval_try.invokeStatic(evaluator.cljc:83)
	at sci.impl.analyzer$analyze_try$reify__4656.eval(analyzer.cljc:933)
	at sci.impl.analyzer$return_do$reify__4313.eval(analyzer.cljc:80)
	at sci.impl.analyzer$analyze_let_STAR_$reify__4588.eval(analyzer.cljc:618)
	at sci.impl.evaluator$eval_def.invokeStatic(evaluator.cljc:27)
	at sci.impl.analyzer$analyze_def$reify__4610.eval(analyzer.cljc:769)
	at sci.impl.interpreter$eval_form_STAR_.invokeStatic(interpreter.cljc:44)
	at sci.impl.interpreter$eval_form.invokeStatic(interpreter.cljc:62)
	at sci.impl.interpreter$eval_string_STAR_.invokeStatic(interpreter.cljc:84)
	at sci.impl.interpreter$eval_string_STAR_.invoke(interpreter.cljc:70)
	at sci.impl.interpreter$eval_string_STAR_.invokeStatic(interpreter.cljc:72)
	at sci.core$eval_string_STAR_.invokeStatic(core.cljc:281)
	at babashka.main$exec$fn__29784$fn__29824$fn__29825.invoke(main.clj:1095)
	at babashka.main$exec$fn__29784$fn__29824.invoke(main.clj:1095)
	at babashka.main$exec$fn__29784.invoke(main.clj:1085)
	at clojure.lang.AFn.applyToHelper(AFn.java:152)
	at clojure.lang.AFn.applyTo(AFn.java:144)
	at clojure.core$apply.invokeStatic(core.clj:667)
	at clojure.core$with_bindings_STAR_.invokeStatic(core.clj:1990)
	at clojure.core$with_bindings_STAR_.doInvoke(core.clj:1990)
	at clojure.lang.RestFn.invoke(RestFn.java:428)
	at babashka.main$exec.invokeStatic(main.clj:876)
	at babashka.main$main.invokeStatic(main.clj:1296)
	at babashka.main$main.doInvoke(main.clj:1239)
	at clojure.lang.RestFn.applyTo(RestFn.java:140)
	at clojure.core$apply.invokeStatic(core.clj:667)
	at babashka.main$_main.invokeStatic(main.clj:1308)
	at babashka.main$_main.doInvoke(main.clj:1300)
	at clojure.lang.RestFn.applyTo(RestFn.java:140)
	at babashka.main.main(Unknown Source)
	at java.base@25/java.lang.invoke.LambdaForm$DMH/sa346b79c.invokeStaticInit(LambdaForm$DMH)
clojure.lang.ExceptionInfo: Unable to resolve symbol: file-store/clear-caches! {:type :sci/error, :line 12, :column 1, :file "/home/runner/work/isaac/isaac/spec/isaac/drive/turn_spec.clj", :phase "analysis"}
	... 278 stack levels elided ...


Error while executing task: ci
```
