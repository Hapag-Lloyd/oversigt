# Dependencies Report: The Dependencies Report fails resolving some Atlassian specific sub-dependencies.
function suppressAtlassianDependenciesReport() {
	cat < /dev/stdin \
	| grep --invert-match --perl-regexp "^\\[INFO\\] Unable to create Maven project from repository for artifact 'com\\.atlassian\\..*', for more information run with -X$"
	| grep --invert-match --perl-regexp "^\\[WARNING\\] Failed to build parent project for com\\.atlassian\\..*$"
	| grep --invert-match --perl-regexp "^\\[WARNING\\] The POM for .*atlassian.* is missing, no dependency information available$"
}

cat < /dev/stdin \
| suppressAtlassianDependenciesReport
