project = "Vector"
copyright = "2024, Vector Contributors"
author = "Vector Contributors"
release = "1.0.0-SNAPSHOT"

extensions = [
    "sphinx_copybutton",
    "myst_parser",
    "sphinxcontrib.mermaid",
]

myst_enable_extensions = [
    "colon_fence",
    "deflist",
]

myst_fence_as_directive = ["mermaid"]

mermaid_version = "11.4.0"

templates_path = ["_templates"]
source_suffix = {
    ".rst": "restructuredtext",
    ".md": "markdown",
}
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]

html_theme = "furo"
html_static_path = ["_static"]

html_theme_options = {
    "source_repository": "https://github.com/earth1283/vector",
    "source_branch": "main",
    "source_directory": "docs/",
    "footer_icons": [
        {
            "name": "GitHub",
            "url": "https://github.com/earth1283/vector",
            "html": "",
            "class": "fa-brands fa-github fa-2x",
        },
    ],
}

pygments_style = "monokai"
pygments_dark_style = "monokai"
