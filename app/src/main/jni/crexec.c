/*
 * crexec —— 读皇室战争进程内存，扫描战斗实体。
 *
 * 编译产物是 .so，但导出 main()，由 root shell 执行：
 *   su -c "<appdir>/libcrexec.so <pid>"
 * 通过 stdout 输出 "gid own x y cur max card" 行。
 *
 * 只依赖 process_vm_readv，不做 ptrace，不影响游戏运行。
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/uio.h>

static pid_t TARGET;

static int rd(uint64_t addr, void *buf, size_t n) {
    struct iovec l = {buf, n};
    struct iovec r = {(void *) (uintptr_t) addr, n};
    return process_vm_readv(TARGET, &l, 1, &r, 1, 0) == (ssize_t) n ? 0 : -1;
}

static int32_t rd_i32(uint64_t addr) {
    int32_t v = 0;
    if (rd(addr, &v, 4) < 0) return 0;
    return v;
}

typedef struct {
    uint32_t gid;
    int32_t own, x, y, cur, max, card;
    uint64_t ent;
} Rec;

#define MAX_REC 512
static Rec R[MAX_REC];
static int N = 0;

/*
 * HP 组件判据（16 字节对齐处）：
 *   +0x00 cur (0 < cur <= max)
 *   +0x04 max (20..20000)
 *   +0x08 0
 *   +0x0c 100
 *   +0x10 max
 *   +0x14 max
 * 命中后实体指针 = hp_addr - 8
 */
static int hp_component_ok(const unsigned char *b, int32_t *cur, int32_t *mx) {
    int32_t h0, h1, h2, h3, h4, h5;
    memcpy(&h0, b, 4);
    memcpy(&h1, b + 4, 4);
    memcpy(&h2, b + 8, 4);
    memcpy(&h3, b + 12, 4);
    memcpy(&h4, b + 16, 4);
    memcpy(&h5, b + 20, 4);
    if (h1 < 20 || h1 > 20000) return 0;
    if (h0 <= 0 || h0 > h1) return 0;
    if (h2 != 0 || h3 != 100) return 0;
    if (h4 != h1 || h5 != h1) return 0;
    *cur = h0;
    *mx = h1;
    return 1;
}

static void try_entity(uint64_t ent, int32_t cur, int32_t mx) {
    if (ent < 0x1000 || (ent & 1)) return;

    unsigned char e[0x100];
    if (rd(ent, e, sizeof(e)) < 0) return;

    uint64_t xy;
    memcpy(&xy, e + 0x80, 8);
    uint32_t X = (uint32_t) ((xy >> 32) & 0xFFFFFFFF);
    uint32_t Y = (uint32_t) (xy & 0xFFFFFFFF);
    if (X > 40000 || Y > 40000) return;
    if (X == 0 && Y == 0) return;

    uint64_t gd;
    memcpy(&gd, e + 8, 8);
    uint32_t gid = (uint32_t) (gd & 0xFFFFFFFF);
    if (gid == 0) return;

    int32_t card;
    memcpy(&card, e + 0xac, 4);

    /* 只保留塔和单位 */
    if (card == -1) {
        if (gid < 5000000 || gid > 5000005) return;
    }

    /* +0x78: 1 = 我方, 0 = 敌方（塔和单位同一语义） */
    int32_t a78 = rd_i32(ent + 0x78);
    int32_t own = (a78 == 1) ? 1 : 0;

    if (N >= MAX_REC) return;
    R[N].gid = gid;
    R[N].own = own;
    R[N].x = (int32_t) X;
    R[N].y = (int32_t) Y;
    R[N].cur = cur;
    R[N].max = mx;
    R[N].card = card;
    R[N].ent = ent;
    N++;
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <pid>\n", argv[0]);
        return 2;
    }
    TARGET = (pid_t) atoi(argv[1]);

    char maps_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", TARGET);
    FILE *mf = fopen(maps_path, "r");
    if (!mf) return 1;

    const size_t CH = 32 * 1024 * 1024;
    unsigned char *buf = malloc(CH + 64);
    if (!buf) return 1;

    char line[512];
    while (fgets(line, sizeof(line), mf)) {
        uint64_t lo, hi;
        char pm[8] = {0};
        if (sscanf(line, "%lx-%lx %7s", &lo, &hi, pm) != 3) continue;
        if (pm[0] != 'r' || pm[1] != 'w') continue;
        if (!strstr(line, "scudo")) continue;
        if (strstr(line, "reserve")) continue;

        for (uint64_t p = lo; p < hi; p += CH) {
            size_t w = (hi - p) < CH ? (hi - p) : CH;
            struct iovec li = {buf, w};
            struct iovec ri = {(void *) (uintptr_t) p, w};
            ssize_t got = process_vm_readv(TARGET, &li, 1, &ri, 1, 0);
            if (got <= 0) break;

            for (ssize_t i = 0; i + 24 <= got; i += 4) {
                int32_t cur, mx;
                if (!hp_component_ok(buf + i, &cur, &mx)) continue;
                /* HP 组件 +0x08 指向实体 → 实体在 hp_addr - 8 */
                uint64_t ent = 0;
                if (rd(p + i - 8, &ent, 8) < 0) continue;
                try_entity(ent, cur, mx);
            }
            if ((size_t) got < w) break;
        }
    }

    fclose(mf);
    free(buf);

    for (int i = 0; i < N; i++) {
        printf("%u %d %d %d %d %d %d\n",
               R[i].gid, R[i].own, R[i].x, R[i].y, R[i].cur, R[i].max, R[i].card);
    }
    return 0;
}