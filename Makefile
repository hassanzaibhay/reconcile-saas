# reconcile-saas — build / test / gate
# Gradle multi-module (Spring Boot 3.5, Spring Modulith, Testcontainers).
# All test tasks require Docker running (Testcontainers: postgres:16 + redis:7 + Ryuk).

GRADLE      := ./gradlew
GRADLE_OPTS := --no-daemon
LOG_DIR     := build/gate-logs
GATE_LOG    := $(LOG_DIR)/gate-$(shell date +%Y%m%d-%H%M%S).log

.DEFAULT_GOAL := help
.PHONY: help build test integration-test modulith format format-check \
        clean run deps gate ci verify

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| sort \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

build: ## Assemble everything, no tests
	$(GRADLE) $(GRADLE_OPTS) assemble

test: ## Unit tests (all modules)
	$(GRADLE) $(GRADLE_OPTS) test

integration-test: ## Integration tests (Testcontainers — needs Docker)
	$(GRADLE) $(GRADLE_OPTS) integrationTest

modulith: ## ArchUnit / Spring Modulith boundary verification
	$(GRADLE) $(GRADLE_OPTS) :modulith-verification:test

format: ## Apply Spotless formatting in place
	$(GRADLE) $(GRADLE_OPTS) spotlessApply

format-check: ## Fail if formatting is off (what CI runs)
	$(GRADLE) $(GRADLE_OPTS) spotlessCheck

deps: ## Print dependency tree for the app module
	$(GRADLE) $(GRADLE_OPTS) :app:dependencies

run: ## Boot the app module locally
	$(GRADLE) $(GRADLE_OPTS) :app:bootRun

clean: ## Remove all build output
	$(GRADLE) $(GRADLE_OPTS) clean

# Full evidence gate — mirrors the manual review discipline:
#   --rerun-tasks forces real execution (no FROM-CACHE/UP-TO-DATE masking a skipped test),
#   tee preserves the log, PIPESTATUS captures gradle's real exit (not tee's),
#   then assert no cache hit landed on an actual test task.
gate: ## Full clean gate with cache-hit assertion (the review bar)
	@mkdir -p $(LOG_DIR)
	@set -o pipefail; \
	$(GRADLE) $(GRADLE_OPTS) clean spotlessCheck test integrationTest \
		:modulith-verification:test --rerun-tasks --info 2>&1 | tee $(GATE_LOG); \
	EXIT=$${PIPESTATUS[0]}; \
	echo "gradle exit: $$EXIT"; \
	if grep -E '^> Task' $(GATE_LOG) | grep -iE '(FROM-CACHE|UP-TO-DATE)$$' | grep -iE ':(test|integrationTest)\b' \
			| grep -vE ':(testClasses|integrationTestClasses|compileTestJava)'; then \
		echo "GATE FAIL: cache hit on a test task"; exit 1; \
	else echo "NO CACHE HITS ON TEST TASKS"; fi; \
	test $$EXIT -eq 0 || { echo "GATE FAIL: gradle exit $$EXIT"; exit $$EXIT; }; \
	echo "GATE PASS — log: $(GATE_LOG)"

verify: gate ## Alias for gate

ci: format-check build test integration-test modulith ## What CI runs (no --rerun-tasks)
