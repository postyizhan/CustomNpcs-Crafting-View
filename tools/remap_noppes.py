#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把构建产物里的官方 CustomNPCs 包名重写为魔改版包名。

源码始终按官方包名 noppes.npcs.* 编译，本脚本在编译完成后对产物 jar 内
class 文件的常量池做字节级重写，另存为 -modified 变体，因此源码、依赖和
build.gradle 都不需要改动。

映射关系（长前缀优先）：
    noppes/npcs/client/ -> noppes/client/
    noppes/npcs/        -> noppes/common/

新旧包名长度不同，无法做等长字节替换。但 class 文件中所有类名、描述符、
Signature 以及反射用的点分字符串都是 CONSTANT_Utf8 常量池项，改内容并同步
修正 2 字节长度前缀即可；常量池之后的结构（含 StackMapTable）只按索引引用
常量池，不含绝对字节偏移，所以整体长度变化是安全的。

只依赖标准库。
"""

import argparse
import json
import os
import struct
import sys
import zipfile

# ---------------------------------------------------------------- 常量

# 有序：长前缀必须排在前面，否则 noppes/npcs/client/ 会先被短规则吃掉。
MAPPINGS = [
    ("noppes/npcs/client/", "noppes/client/"),
    ("noppes/npcs/", "noppes/common/"),
    ("noppes.npcs.client.", "noppes.client."),
    ("noppes.npcs.", "noppes.common."),
]

DEFAULT_JARS = [
    # 只默认处理 SRG 产物：不带后缀的 jar 用的是 MCP 开发期名称（如
    # GuiContainer.inventorySlots），只在 runClient 里有效；装进真实游戏会
    # 报 NoSuchFieldError。发布必须用 renamer 产出的 -srg 变体。
    "build/libs/CustomNpcs-Crafting-View-srg.jar",
]

# 仅供 --include-dev-jar 使用的开发期产物
DEV_JAR = "build/libs/CustomNpcs-Crafting-View.jar"

DEFAULT_NPC_JAR = "customnpcs_1.6.4.jar"

DEFAULT_OUT_SUFFIX = "-modified"

EXPECTED_MODID = "customnpcs"

# 项目实际引用的 4 个 noppes 类：官方内部名 -> 魔改版内部名
CLASS_MAP = {
    "noppes/npcs/containers/ContainerCarpentryBench":
        "noppes/common/containers/ContainerCarpentryBench",
    "noppes/npcs/controllers/RecipeCarpentry":
        "noppes/common/controllers/RecipeCarpentry",
    "noppes/npcs/controllers/RecipeController":
        "noppes/common/controllers/RecipeController",
    "noppes/npcs/client/gui/player/GuiNpcCarpentryBench":
        "noppes/client/gui/player/GuiNpcCarpentryBench",
}

# 代码实际用到的成员，按魔改版内部名索引
REQUIRED_MEMBERS = {
    "noppes/common/controllers/RecipeCarpentry": [
        "id", "name", "recipeWidth", "recipeHeight", "recipeOutput",
        "ignoreDamage", "getCraftingItem", "readNBT", "writeNBT",
    ],
    "noppes/common/controllers/RecipeController": [
        "instance", "globalRecipes", "anvilRecipes", "reloadGlobalRecipes",
    ],
    "noppes/common/containers/ContainerCarpentryBench": [
        "craftMatrix",
    ],
    "noppes/client/gui/player/GuiNpcCarpentryBench": [],
}

# 已重写标志：输入 jar 里出现这些前缀说明是 -modified 产物，不能二次重写
REWRITTEN_MARKERS = ["noppes/common/", "noppes/client/", "noppes.common.", "noppes.client."]

MODID_DEPENDENCY_PREFIXES = ["required-after:", "required-before:", "after:", "before:"]

# net.minecraft 成员在 SRG 产物里必然是这两种前缀；出现人类可读名
# 就说明这是 MCP 开发期产物，装进游戏会报 NoSuchFieldError。
SRG_MEMBER_PREFIXES = ("field_", "func_")

OBFUSCATED_OWNER_PREFIX = "net/minecraft/"

# 这些 owner 虽在 net/minecraft 下，但成员名不参与混淆
OWNER_WHITELIST_PREFIXES = (
    "net/minecraft/launchwrapper/",
)

CLASS_MAGIC = 0xCAFEBABE

# tag -> 该项在 tag 字节之后的固定字节数；Utf8(1) 变长，单独处理
TAG_SIZES = {
    3: 4,   # Integer
    4: 4,   # Float
    5: 8,   # Long   (占 2 槽位)
    6: 8,   # Double (占 2 槽位)
    7: 2,   # Class
    8: 2,   # String
    9: 4,   # Fieldref
    10: 4,  # Methodref
    11: 4,  # InterfaceMethodref
    12: 4,  # NameAndType
    15: 3,  # MethodHandle
    16: 2,  # MethodType
    17: 4,  # Dynamic
    18: 4,  # InvokeDynamic
    19: 2,  # Module
    20: 2,  # Package
}

TWO_SLOT_TAGS = (5, 6)

MAX_UTF8_LEN = 0xFFFF


class RemapError(Exception):
    """校验失败或 class 解析失败。"""


# ---------------------------------------------------------------- class 解析


def _parse_constant_pool(data, entry_name):
    """解析常量池，返回 (utf8_entries, utf8_values, refs, pool_end)。

    utf8_entries: [(start, end, raw_bytes)]，按出现顺序，start/end 不含长度前缀。
    utf8_values:  {常量池索引: 字符串}。
    refs:         {常量池索引: (tag, payload_bytes)}，仅含 Class/NameAndType/
                  Fieldref/Methodref/InterfaceMethodref，用于解析成员引用。
    pool_end:     常量池之后第一个字节的偏移。

    Long/Double 占 2 个槽位，索引需多加 1。遇未知 tag 直接报错，不猜测。
    """
    if len(data) < 10:
        raise RemapError("%s: 文件过短，不是合法 class" % entry_name)
    magic = struct.unpack_from(">I", data, 0)[0]
    if magic != CLASS_MAGIC:
        raise RemapError("%s: 魔数不是 0xCAFEBABE (实际 0x%08X)" % (entry_name, magic))

    count = struct.unpack_from(">H", data, 8)[0]
    entries = []
    values = {}
    refs = {}
    off = 10
    index = 1
    while index < count:
        if off >= len(data):
            raise RemapError("%s: 常量池越界 (索引 %d)" % (entry_name, index))
        tag = data[off]
        off += 1
        if tag == 1:  # CONSTANT_Utf8
            if off + 2 > len(data):
                raise RemapError("%s: Utf8 长度字段越界 (索引 %d)" % (entry_name, index))
            length = struct.unpack_from(">H", data, off)[0]
            start = off + 2
            end = start + length
            if end > len(data):
                raise RemapError("%s: Utf8 内容越界 (索引 %d)" % (entry_name, index))
            raw = data[start:end]
            entries.append((start, end, raw))
            values[index] = raw.decode("utf-8", "replace")
            off = end
        elif tag in TAG_SIZES:
            end = off + TAG_SIZES[tag]
            if end > len(data):
                raise RemapError(
                    "%s: 常量项越界 (索引 %d, tag %d)" % (entry_name, index, tag))
            if tag in (7, 9, 10, 11, 12):
                refs[index] = (tag, data[off:end])
            off = end
            if tag in TWO_SLOT_TAGS:
                index += 1
        else:
            raise RemapError("%s: 未知常量池 tag %d (索引 %d)" % (entry_name, tag, index))
        index += 1

    return entries, values, refs, off


def _replace_all(raw, mappings):
    """对单个 Utf8 字节串按有序映射做子串替换，返回 (新字节串, 替换次数)。"""
    total = 0
    out = raw
    for old, new in mappings:
        old_b = old.encode("ascii")
        if old_b not in out:
            continue
        hits = out.count(old_b)
        out = out.replace(old_b, new.encode("ascii"))
        total += hits
    return out, total


def rewrite_class(data, entry_name, mappings):
    """重写一个 class 的常量池 Utf8 项，返回 (新字节串, 替换次数)。"""
    utf8_entries, _values, _refs, _pool_end = _parse_constant_pool(data, entry_name)

    pieces = []
    cursor = 0
    replaced = 0

    for start, end, raw in utf8_entries:
        new_raw, hits = _replace_all(raw, mappings)
        if hits == 0:
            continue
        if len(new_raw) > MAX_UTF8_LEN:
            raise RemapError(
                "%s: 重写后 Utf8 长度 %d 超出 65535 上限" % (entry_name, len(new_raw)))
        # start-2 是 2 字节长度前缀的位置，需要一起换掉
        pieces.append(data[cursor:start - 2])
        pieces.append(struct.pack(">H", len(new_raw)))
        pieces.append(new_raw)
        cursor = end
        replaced += hits

    if replaced == 0:
        return data, 0

    # 常量池尾部 + 常量池之后的所有结构原样拷贝
    pieces.append(data[cursor:])
    return b"".join(pieces), replaced


# ---------------------------------------------------------------- 成员表解析


def _skip_attributes(data, off, entry_name):
    count = struct.unpack_from(">H", data, off)[0]
    off += 2
    for _ in range(count):
        if off + 6 > len(data):
            raise RemapError("%s: 属性表越界" % entry_name)
        length = struct.unpack_from(">I", data, off + 2)[0]
        off += 6 + length
        if off > len(data):
            raise RemapError("%s: 属性内容越界" % entry_name)
    return off


def read_member_names(data, entry_name):
    """真正解析字段表与方法表，返回声明的成员名集合。

    不能直接拿常量池里的字符串当成员名：常量池还包含对外部类的引用。
    """
    _entries, values, _refs, off = _parse_constant_pool(data, entry_name)

    # access_flags(2) + this_class(2) + super_class(2)
    off += 6
    if off + 2 > len(data):
        raise RemapError("%s: 接口计数越界" % entry_name)
    interfaces_count = struct.unpack_from(">H", data, off)[0]
    off += 2 + interfaces_count * 2

    names = set()
    for _ in range(2):  # fields 然后 methods，结构相同
        if off + 2 > len(data):
            raise RemapError("%s: 成员计数越界" % entry_name)
        count = struct.unpack_from(">H", data, off)[0]
        off += 2
        for _ in range(count):
            if off + 8 > len(data):
                raise RemapError("%s: 成员表越界" % entry_name)
            name_index = struct.unpack_from(">H", data, off + 2)[0]
            names.add(values.get(name_index, ""))
            off = _skip_attributes(data, off + 6, entry_name)
    return names


def find_mcp_dev_members(data, entry_name):
    """找出引用的 net.minecraft 成员里仍是 MCP 人类可读名的部分。

    返回 ["owner.member"]。非空即说明这是开发期产物，装进真实游戏会
    报 NoSuchFieldError / NoSuchMethodError。
    """
    _entries, values, refs, _end = _parse_constant_pool(data, entry_name)

    def class_name(idx):
        ref = refs.get(idx)
        if not ref or ref[0] != 7:
            return None
        return values.get(struct.unpack_from(">H", ref[1], 0)[0])

    bad = []
    for tag, payload in refs.values():
        if tag not in (9, 10, 11):  # Fieldref / Methodref / InterfaceMethodref
            continue
        owner = class_name(struct.unpack_from(">H", payload, 0)[0])
        nat = refs.get(struct.unpack_from(">H", payload, 2)[0])
        if not owner or not nat or nat[0] != 12:
            continue
        member = values.get(struct.unpack_from(">H", nat[1], 0)[0])
        if not member:
            continue
        if not owner.startswith(OBFUSCATED_OWNER_PREFIX):
            continue
        if owner.startswith(OWNER_WHITELIST_PREFIXES):
            continue
        if member.startswith(SRG_MEMBER_PREFIXES) or member.startswith("<"):
            continue
        bad.append("%s.%s" % (owner, member))
    return sorted(set(bad))


def assert_srg_named(jar_path, allow_dev_names, log):
    """确认产物用的是 SRG 名称；MCP 开发期名称默认直接报错。"""
    offenders = {}
    with zipfile.ZipFile(jar_path) as z:
        for info in z.infolist():
            if not info.filename.endswith(".class"):
                continue
            bad = find_mcp_dev_members(z.read(info), info.filename)
            if bad:
                offenders[info.filename] = bad

    if not offenders:
        return

    lines = []
    for name in sorted(offenders):
        lines.append("  %s: %s" % (name, ", ".join(offenders[name][:4])))
    detail = "\n".join(lines[:8])
    message = (
        "%s 引用了 MCP 开发期成员名（非 field_/func_），说明这是开发期产物，"
        "只能在 runClient 下用；装进真实游戏会报 NoSuchFieldError。\n"
        "请改用 renamer 产出的 -srg 变体。片段:\n%s" % (jar_path, detail))
    if allow_dev_names:
        log("警告: %s" % message)
    else:
        raise RemapError(message)


# ---------------------------------------------------------------- 校验


def read_modid(npc_zip):
    """读 jar 内 mcmod.info 的 modid；读不到返回 None。"""
    try:
        raw = npc_zip.read("mcmod.info")
    except KeyError:
        return None
    try:
        meta = json.loads(raw.decode("utf-8", "replace"))
    except ValueError:
        return None
    if isinstance(meta, dict):
        meta = meta.get("modList") or []
    if not isinstance(meta, list) or not meta:
        return None
    first = meta[0]
    return first.get("modid") if isinstance(first, dict) else None


def verify_npc_jar(npc_jar, allow_modid_mismatch, log):
    """校验魔改 jar：4 个类存在、成员名未变、modid 符合预期。

    返回探测到的 modid（可能为 None）。任一不符即抛 RemapError。
    """
    problems = []
    with zipfile.ZipFile(npc_jar) as z:
        names = set(z.namelist())

        for official, modified in sorted(CLASS_MAP.items()):
            entry = modified + ".class"
            if entry in names:
                log("  类 OK  %s" % modified)
            else:
                problems.append("缺少类 %s（对应官方 %s）" % (modified, official))

        for cls, members in sorted(REQUIRED_MEMBERS.items()):
            entry = cls + ".class"
            if entry not in names or not members:
                continue
            try:
                declared = read_member_names(z.read(entry), entry)
            except RemapError as exc:
                problems.append("无法解析 %s: %s" % (entry, exc))
                continue
            missing = [m for m in members if m not in declared]
            if missing:
                problems.append("%s 缺少成员: %s" % (cls, ", ".join(missing)))
            else:
                log("  成员 OK  %s (%d 项)" % (cls, len(members)))

        modid = read_modid(z)

    log("  modid = %s" % (modid if modid else "<未读到>"))
    if modid != EXPECTED_MODID:
        message = "modid 为 %r，预期 %r" % (modid, EXPECTED_MODID)
        if allow_modid_mismatch:
            log("  警告: %s（--allow-modid-mismatch 已忽略）" % message)
        else:
            problems.append(
                message + "；如确认无误请用 --modid-override 改写依赖声明，"
                "或用 --allow-modid-mismatch 跳过本项")

    if problems:
        raise RemapError("魔改 jar 校验失败:\n  - " + "\n  - ".join(problems))
    return modid


# ---------------------------------------------------------------- jar 处理


def build_mappings(modid_override):
    """基础包名映射，必要时追加 modid 依赖声明改写。"""
    mappings = list(MAPPINGS)
    if modid_override:
        for prefix in MODID_DEPENDENCY_PREFIXES:
            mappings.append((prefix + EXPECTED_MODID, prefix + modid_override))
    return mappings


def check_not_rewritten(jar_path):
    """输入 jar 已含魔改包名则报错，避免二次重写。"""
    markers = [m.encode("ascii") for m in REWRITTEN_MARKERS]
    with zipfile.ZipFile(jar_path) as z:
        for info in z.infolist():
            if not info.filename.endswith(".class"):
                continue
            data = z.read(info)
            for marker in markers:
                if marker in data:
                    raise RemapError(
                        "%s 已包含魔改包名引用 %s，看起来是已重写过的产物；"
                        "请对原始产物运行。" % (jar_path, marker.decode("ascii")))


def process_jar(src, dst, mappings, modid_override, dry_run, log):
    """重写单个 jar，返回 (总替换次数, 变动的 class 数)。"""
    total = 0
    touched = 0
    out_entries = []

    with zipfile.ZipFile(src) as zin:
        for info in zin.infolist():
            data = zin.read(info)
            if info.filename.endswith(".class"):
                new_data, hits = rewrite_class(data, info.filename, mappings)
                if hits:
                    total += hits
                    touched += 1
                    log("    %s: %d 处" % (info.filename, hits))
                data = new_data
            elif info.filename == "mcmod.info" and modid_override:
                data = data.replace(
                    ('"%s"' % EXPECTED_MODID).encode("utf-8"),
                    ('"%s"' % modid_override).encode("utf-8"))
            out_entries.append((info, data))

    if total == 0:
        raise RemapError(
            "%s 内未找到任何官方 noppes 包名引用，产物路径或映射表可能不对。" % src)

    if not dry_run:
        os.makedirs(os.path.dirname(os.path.abspath(dst)), exist_ok=True)
        with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
            for info, data in out_entries:
                # 新建 ZipInfo：不携带原 CRC/尺弸，保留名称、时间戳、压缩方式
                out_info = zipfile.ZipInfo(info.filename, date_time=info.date_time)
                out_info.compress_type = info.compress_type
                out_info.external_attr = info.external_attr
                out_info.internal_attr = info.internal_attr
                out_info.create_system = info.create_system
                zout.writestr(out_info, data)

    return total, touched


def default_output(jar_path, suffix, dev=False):
    """输出文件名；开发期产物额外标 -devonly，避免被误装进游戏。"""
    base, ext = os.path.splitext(jar_path)
    return base + suffix + ("-devonly" if dev else "") + ext


# ---------------------------------------------------------------- CLI


def parse_args(argv):
    parser = argparse.ArgumentParser(
        description="把构建产物内的官方 CustomNPCs 包名重写为魔改版包名。",
        formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--npc-jar", default=DEFAULT_NPC_JAR,
                        help="魔改版 CustomNPCs jar，仅作校验输入（默认 %(default)s）")
    parser.add_argument("--jars", nargs="+", default=None,
                        help="要重写的产物 jar（默认只处理 -srg 变体）")
    parser.add_argument("--include-dev-jar", action="store_true",
                        help="额外处理 MCP 名称的开发期产物（仅 runClient 可用）")
    parser.add_argument("--allow-dev-names", action="store_true",
                        help="允许产物带 MCP 开发期成员名（默认报错）")
    parser.add_argument("--out-suffix", default=DEFAULT_OUT_SUFFIX,
                        help="输出文件名后缀（默认 %(default)s）")
    parser.add_argument("--skip-verify", action="store_true",
                        help="跳过全部魔改 jar 校验")
    parser.add_argument("--allow-modid-mismatch", action="store_true",
                        help="modid 与 customnpcs 不一致时仍继续")
    parser.add_argument("--modid-override", default=None,
                        help="把依赖声明与 mcmod.info 里的 modid 改为此值")
    parser.add_argument("--force", action="store_true",
                        help="输出文件已存在时覆盖")
    parser.add_argument("--dry-run", action="store_true",
                        help="只统计不写文件")
    parser.add_argument("-v", "--verbose", action="store_true",
                        help="打印逐类替换明细")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv if argv is not None else sys.argv[1:])

    def log(msg):
        print(msg)

    def vlog(msg):
        if args.verbose:
            print(msg)

    # 工作目录统一到仓库根，让默认相对路径不依赖谁来调用
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    os.chdir(repo_root)

    jars = args.jars if args.jars else list(DEFAULT_JARS)
    if args.include_dev_jar and not args.jars:
        jars.append(DEV_JAR)
    existing = [j for j in jars if os.path.isfile(j)]
    missing = [j for j in jars if not os.path.isfile(j)]

    for j in missing:
        log("跳过（不存在）: %s" % j)

    if not existing:
        log("错误: 没有可处理的产物 jar，请先构建或用 --jars 指定。")
        return 2

    # 校验：魔改 jar 缺失时跳过，不阻断流程
    if args.skip_verify:
        log("已跳过魔改 jar 校验（--skip-verify）")
    elif not os.path.isfile(args.npc_jar):
        log("警告: 未找到魔改 jar %s，跳过校验。" % args.npc_jar)
    else:
        log("校验魔改 jar: %s" % args.npc_jar)
        try:
            verify_npc_jar(args.npc_jar, args.allow_modid_mismatch, log)
        except (RemapError, zipfile.BadZipFile) as exc:
            log("错误: %s" % exc)
            return 1
        log("校验通过。")

    mappings = build_mappings(args.modid_override)
    if args.modid_override:
        log("modid 将改写为: %s" % args.modid_override)

    grand_total = 0
    for jar in existing:
        # 名字像开发期产物 -> 输出标 -devonly；但“允许 MCP 名”只能由
        # --include-dev-jar / --allow-dev-names 显式授权，不能靠路径巧合绕过。
        is_dev = (jar == DEV_JAR)
        dst = default_output(jar, args.out_suffix, dev=is_dev)
        log("")
        log("处理: %s" % jar)
        log("  -> %s%s" % (dst, "（dry-run，不写入）" if args.dry_run else ""))

        if os.path.exists(dst) and not args.force and not args.dry_run:
            log("错误: 输出已存在，需 --force 覆盖: %s" % dst)
            return 1

        try:
            check_not_rewritten(jar)
            # 开发期产物装进游戏会报 NoSuchFieldError，在写出前就拦下
            allow_dev = args.allow_dev_names or (is_dev and args.include_dev_jar)
            assert_srg_named(jar, allow_dev, log)
            total, touched = process_jar(
                jar, dst, mappings, args.modid_override, args.dry_run, vlog)
        except (RemapError, zipfile.BadZipFile) as exc:
            log("错误: %s" % exc)
            return 1

        log("  共 %d 个 class 变动，%d 处包名替换。" % (touched, total))
        grand_total += total

    log("")
    log("完成：%d 个 jar，共 %d 处替换。%s" % (
        len(existing), grand_total, "（dry-run，未写入任何文件）" if args.dry_run else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
