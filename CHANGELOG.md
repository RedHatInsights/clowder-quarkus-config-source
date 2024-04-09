# Changelog

## [2.6.1](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v2.6.0...v2.6.1) (2024-04-09)


### Bug Fixes

* null pointer exception when attempting to read a system property ([#245](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/245)) ([e5191e4](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/e5191e453476374e9d0624bf277b4e163c908348))

## [2.6.0](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v2.5.1...v2.6.0) (2024-04-04)


### Features

* Refactor config source to not keep in memory all the properties ([#218](https://github.com/RedHatInsights/clowder-quarkus-config-source/pull/218)) ([318e4b8](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/318e4b82c3fcc10ffda22fc496bc5dd681535758))

## [2.5.1](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v2.5.0...v2.5.1) (2024-03-01)


### Bug Fixes

* RHCLOUD-28459 Fix Unleash API URL ([#230](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/230)) ([bacf632](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/bacf632827838557937fb5d7f3a7546d8ad97318))

## [2.5.0](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v2.4.0...v2.5.0) (2024-02-29)


### Features

* Bump Quarkus to 3.7.4 ([#227](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/227)) ([ff3495a](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/ff3495a16682b25330617ac9a2586b5cc08797eb))

## [2.4.0](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v2.3.0...v2.4.0) (2024-02-13)


### Features

* [RHCLOUD-30340] add Kafka SSL config with a certificate provide… ([#211](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/211)) ([6922171](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/69221710f4a9a640093690f3ef09cbb8168ec51f))

## [2.3.0](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v2.2.0...v2.3.0) (2024-01-12)


### Features

* Resolve computed properties for Kafka topics ([c485a66](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/c485a6620ac559303ced114c74fb59dd036177e3))

## [2.2.0](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v2.1.0...v2.2.0) (2024-01-10)


### Features

* Be able to expose Kafka config keys from ClowderConfigSource ([#198](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/198)) ([35f5e7c](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/35f5e7cdb425b1b245226a821fa3a088f5168bbf))
* Bump Java from 11 to 17 ([#159](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/159)) ([eb65d59](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/eb65d5953ddeb52336ed620d6ac45f7e6af1d875))
* Fix project version and trigger a new release ([#153](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/153)) ([fe5a37e](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/fe5a37e04a2c06d332ab2622c2e520a8de76e12d))
* support Camel Kafka props ([#96](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/96)) ([3fc6e40](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/3fc6e40fc919a09017112605fea85368f5658f78))


### Bug Fixes

* Add nexus-staging-maven-plugin ([#156](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/156)) ([4b0dde5](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/4b0dde5b63b4de87a475503a3ec0b5cc6d5c2f15))
* artefact version ([#200](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/200)) ([56b0ef2](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/56b0ef23353bff6519b1afec7bde862db69fd72a))
* Deploy sources and javadoc ([#160](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/160)) ([e9408d1](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/e9408d1f1fffd71c4d6cf8cddad50c9a47301dd3))
* **logging:** only look for type null ([#87](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/87)) ([0eba62c](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/0eba62c6e484251eabcc1c42de61a52cfdd51f1e))
* restart release 2.1.0 ([#204](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/204)) ([a0d051e](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/a0d051e64b8b335293806e15e52cb7fe461363e0))

## [2.1.0](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v1.5.0...v1.6.0) (2024-01-09)


### Features

* Be able to expose Kafka config keys from ClowderConfigSource ([#198](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/198)) ([35f5e7c](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/35f5e7cdb425b1b245226a821fa3a088f5168bbf))


### Bug Fixes

* artefact version ([#200](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/200)) ([56b0ef2](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/56b0ef23353bff6519b1afec7bde862db69fd72a))

## [2.0.0](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v1.4.1...v1.5.0) (2023-09-08)

This release depends on Quarkus 3.2.5.Final and should NOT be used in an application that depends on Quarkus 2 or lower.

## [1.5.0](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v1.4.1...v1.5.0) (2023-07-03)


### Features

* Bump Java from 11 to 17 ([#159](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/159)) ([eb65d59](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/eb65d5953ddeb52336ed620d6ac45f7e6af1d875))


### Bug Fixes

* Deploy sources and javadoc ([#160](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/160)) ([e9408d1](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/e9408d1f1fffd71c4d6cf8cddad50c9a47301dd3))

## [1.4.1](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v1.4.0...v1.4.1) (2023-07-03)


### Bug Fixes

* Add nexus-staging-maven-plugin ([#156](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/156)) ([4b0dde5](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/4b0dde5b63b4de87a475503a3ec0b5cc6d5c2f15))

## [1.4.0](https://github.com/RedHatInsights/clowder-quarkus-config-source/compare/v1.3.0...v1.4.0) (2023-07-03)


### Features

* Fix project version and trigger a new release ([#153](https://github.com/RedHatInsights/clowder-quarkus-config-source/issues/153)) ([fe5a37e](https://github.com/RedHatInsights/clowder-quarkus-config-source/commit/fe5a37e04a2c06d332ab2622c2e520a8de76e12d))
