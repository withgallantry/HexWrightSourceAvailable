from importlib.resources import Package

from hexdoc.plugin import HookReturn, ModPlugin, ModPluginImpl, ModPluginWithBook, hookimpl
from typing_extensions import override

import hexdoc_hexwright

from .__gradle_version__ import FULL_VERSION, GRADLE_VERSION
from .__version__ import PY_VERSION


class HexwrightPlugin(ModPluginImpl):
    @staticmethod
    @hookimpl
    def hexdoc_mod_plugin(branch: str) -> ModPlugin:
        return HexwrightModPlugin(branch=branch)


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
