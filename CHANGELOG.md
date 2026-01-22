# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- Fixed `/api/v1/groups/{name}` endpoint returning only first value of repeating attributes (`users_names`, `groups_names`). Now uses `getValueCount()` + `getRepeatingString()` pattern to correctly retrieve all values. Also consolidated from 3 queries to 1. (#274)
