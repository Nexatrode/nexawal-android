# Translation review status

The October 12, 2026 audit checks all locale resources for identical key coverage,
nonblank values, duplicate names and preserved format argument positions/types.
Currency names are provided by the OS for the active app locale. Technical IDs,
URLs, symbols and resource aliases intentionally remain unchanged.

`fiat_help_nexatrode` and `about_disclaimer_nexatrode` contain draft translations
in every supported locale. They were machine-generated and have not been signed
off by fluent speakers. Review security/privacy wording before claiming human
translation quality. Complete resource coverage is not a native-language audit.
Full bundled legal documents remain English; native diagnostic details stay
verbatim inside localized UI messages.

Run `:app:testDebugUnitTest --tests '*A11yStringsXmlTest'` for string regressions.
