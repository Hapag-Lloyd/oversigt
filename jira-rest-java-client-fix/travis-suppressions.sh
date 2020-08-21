# JDK8: The Dependencies Report fails resolving some Atlassian specific sub-dependencies.
function suppressJdk8AtlassianDependenciesReport() {
	if [ ${JDK_VERSION} -eq 8 ]; then
		cat < /dev/stdin \
		| grep --invert-match --perl-regexp "^\\[INFO\\] Unable to create Maven project from repository for artifact 'com\\.atlassian\\..*', for more information run with -X$"
		| grep --invert-match --perl-regexp "^\\[WARNING\\] Failed to build parent project for com\\.atlassian\\..*$"
		| grep --invert-match --perl-regexp "^\\[WARNING\\] The POM for .*atlassian.* is missing, no dependency information available$"
	else
		cat < /dev/stdin
	fi
}

# JDK11 and later: Inspecting try-with-resource fails as of https://github.com/spotbugs/spotbugs/issues/259
function suppressJdk11AndLaterSpotbugsTryWithResourceNPE() {
	if [ ${JDK_VERSION} -ge 11 ]; then
		cat < /dev/stdin \
		| grep --invert-match --perl-regexp "^TODO$"
	else
		cat < /dev/stdin
	fi
}

cat < /dev/stdin \
| suppressJdk11AndLaterSpotbugsTryWithResourceNPE \
| suppressJdk8AtlassianDependenciesReport
