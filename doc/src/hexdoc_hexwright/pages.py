from hexdoc_hexcasting.book.page.pages import LookupPatternPage


class FolioPatternPage(
    LookupPatternPage,
    type="hexwright:pattern",
    template_type="hexcasting:pattern",
):
    """A Folio pattern page.

    Patchouli only resolves templates from the book's own namespace, so the Folio has to ship its
    own copy of Hex's pattern template as `hexwright:pattern`. It is the same page underneath, so it
    validates and renders exactly as `hexcasting:pattern`.
    """
