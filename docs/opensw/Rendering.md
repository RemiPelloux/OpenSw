<!--
SPDX-FileCopyrightText: Copyright 2026 OpenSw Project
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Vulkan rendering compatibility

This document records verified renderer behavior that differs between the Maxwell guest API and
Vulkan. It separates implemented emulation from capability-gated fallbacks and known limitations.

## Primitive topology

Vulkan has no native equivalent of Maxwell `LineLoop`. Mapping it to
`VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST` consumed the vertices as triangles and could produce missing or
corrupt geometry.

OpenSw maps the topology to `VK_PRIMITIVE_TOPOLOGY_LINE_STRIP`. Direct draws with at least two
vertices are closed as follows:

- Indexed draws copy the requested index range on the GPU and append a copy of its first index.
- Non-indexed draws bind a temporary sequence `0, 1, ..., n - 1, 0` and preserve `firstVertex` as
  the indexed draw's base vertex.
- Zero- and one-vertex draws retain their original count because they cannot produce a line.
- The emulation is entered only for `LineLoop`; other primitive topologies do not allocate or copy.

Indexed loops with primitive restart cannot be closed by appending one global index because each
restart-delimited strip requires its own closing index. Indirect draws expose their vertex count only
to the GPU. These two cases currently render as open line strips and produce one warning per process.
They no longer use triangle topology, but complete closure requires a dedicated GPU expansion pass.

## Eight-bit index support

`MaxwellToVK::IndexFormat` can return `VK_INDEX_TYPE_UINT8_EXT`, but binding is capability-gated in
the Vulkan buffer cache. When `VK_EXT_index_type_uint8` is unavailable, OpenSw converts the guest
indices to `UINT16` with the existing compute pass when the required 8-bit and 16-bit storage
features are exposed. The Qualcomm safety fallback remains in place for devices that cannot run the
conversion.

## Three-component vertex formats

Maxwell `Size_R8_G8_B8` attributes map to Vulkan `VK_FORMAT_R8G8B8_*` formats. These are vertex
formats, not optimal-tiling image surfaces. `VertexFormat` requests
`VK_FORMAT_FEATURE_VERTEX_BUFFER_BIT` through `Device::GetSupportedFormat`, so support is checked for
the usage that actually matters.

Replacing an unsupported three-byte vertex format with `R8G8B8A8` is not generally safe: it changes
the fetch width from three to four bytes and requires repacking or a shader-aware conversion. OpenSw
therefore does not apply a blind format substitution.

## Depth and stencil classification

`IsZetaFormat` relies on the sentinel layout declared by `PixelFormat`: color entries precede
`MaxColorFormat`, followed by contiguous depth and stencil entries up to `MaxDepthStencilFormat`.
The same ordering contract is used by the central surface classifier. Reordering this enum requires
updating both the sentinels and their compile-time consumers.

## Validation

The line-loop count and sequential closure helpers have native unit coverage for empty, one-vertex,
multi-vertex and overflow-boundary inputs. Host Vulkan tests and the Android ARM64 build must pass
before an APK is published. Rendering fixes are correctness changes; no FPS improvement is claimed
without a separate repeated A/B capture.
