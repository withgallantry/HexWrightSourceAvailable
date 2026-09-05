package com.bluup.hexwright.mixin;

import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumMap;
import java.util.Map;
import java.util.regex.Pattern;

@Mixin(value = TransformPatcher.class, remap = false)
public class IrisPortalShaderMixin {
    @Unique private static final Pattern HEXWRIGHT_MAIN =
        Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void\\s*)?\\)");

    @Inject(method = {"patchVanilla", "patchSodium"}, at = @At("RETURN"), cancellable = true)
    private static void hexwright$clipWorldFragments(CallbackInfoReturnable<Map<PatchShaderType, String>> cir) {
        Map<PatchShaderType, String> shaders = cir.getReturnValue();
        String fragment = shaders.get(PatchShaderType.FRAGMENT);
        if (fragment == null) {
            return;
        }
        var main = HEXWRIGHT_MAIN.matcher(fragment);
        if (!main.find()) {
            throw new IllegalStateException("Iris fragment shader has no main function");
        }
        String clipped = main.replaceFirst("void hexwright_original_main()") + """

            uniform vec4 hexwright_ClipPlane;
            uniform vec2 hexwright_InverseViewport;
            void main() {
                vec4 position = vec4(gl_FragCoord.xy * hexwright_InverseViewport * 2.0 - 1.0,
                                     gl_FragCoord.z * 2.0 - 1.0, 1.0);
                if (dot(position, hexwright_ClipPlane) < 0.0) discard;
                hexwright_original_main();
            }
            """;
        Map<PatchShaderType, String> result = new EnumMap<>(shaders);
        result.put(PatchShaderType.FRAGMENT, clipped);
        cir.setReturnValue(result);
    }
}
