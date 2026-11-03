from importlib.resources import Package

from hexdoc.plugin import (
    HookReturn,
    LoadTaggedUnionsImpl,
    ModPlugin,
    ModPluginImpl,
    ModPluginWithBook,
    hookimpl,
)
from typing_extensions import override

import hexdoc_hexwright

from . import pages
from .__gradle_version__ import FULL_VERSION, GRADLE_VERSION
from .__version__ import PY_VERSION


class HexwrightPlugin(LoadTaggedUnionsImpl, ModPluginImpl):
    @staticmethod
    @hookimpl
    def hexdoc_mod_plugin(branch: str) -> ModPlugin:
        return HexwrightModPlugin(branch=branch)

    @staticmethod
    @hookimpl
    def hexdoc_load_tagged_unions() -> HookReturn[Package]:
        return pages


class HexwrightModPlugin(ModPluginWithBook):
    @property
    @override
    def modid(self) -> str:
        return "hexwright"

    @property
    @override
    def full_version(self) -> str:
        return FULL_VERSION

    @property
    @override
    def mod_version(self) -> str:
        return GRADLE_VERSION

    @property
    @override
    def plugin_version(self) -> str:
        return PY_VERSION

    @override
    def resource_dirs(self) -> HookReturn[Package]:
        # Import lazily because generated content may not exist at import time.
        from hexdoc_hexwright._export import generated

        return generated

    @override
    def jinja_template_root(self) -> tuple[Package, str]:
        return hexdoc_hexwright, "_templates"
