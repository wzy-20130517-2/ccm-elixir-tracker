/*
 * elixir_find —— 定位我方圣水数值的地址。
 *
 * 圣水每 2.8 秒 +1（满 10 停），出牌时 -3/-4，范围 [0,10]。
 * 用「多轮采样 + 轨迹收敛」筛：
 *   1. 全扫 [0,10] 的 int32
 *   2. 等 2.8s 重采，只留「增量 0..1」且仍 <=10 的
 *   3. 重复 4 轮
 *   4. 输出所有活下来的地址 + 当前值
 *
 * 输出：每个地址一行 "<addr> <value>"
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/uio.h>

static pid_t TARGET;

static ssize_t rdm(uint64_t addr, void *buf, size_t n) {
    struct iovec l = {buf, n};
    struct iovec r = {(void *) (uintptr_t) addr, n};
    return process_vm_readv(TARGET, &l, 1, &r, 1, 0);
}

typedef struct {
    uint64_t addr;
    int32_t prev;
} Cand;

#define MAX_CAND 2000000
static Cand *cand = NULL;
static size_t ncand = 0, cap = 0;

static void cand_push(uint64_t a, int32_t v) {
    if (ncand >= MAX_CAND) return;
    if (ncand >= cap) {
        cap = cap ? cap * 2 : 65536;
        cand = realloc(cand, cap * sizeof(Cand));
        if (!cand) _exit(1);
    }
    cand[ncand].addr = a;
    cand[ncand].prev = v;
    ncand++;
}

/** 全量扫描一遍，把 [1,10] 的 int32 收进来 */
static void scan_all(void) {
    char maps_path[64];
    snprintf(maps_path, sizeof(maps_path), "/proc/%d/maps", TARGET);
    FILE *mf = fopen(maps_path, "r");
    if (!mf) return;

    const size_t CH = 32 * 1024 * 1024;
    unsigned char *buf = malloc(CH + 64);
    if (!buf) { fclose(mf); return; }

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
            for (ssize_t i = 0; i + 4 <= got; i += 4) {
                int32_t v;
                memcpy(&v, buf + i, 4);
                if (v >= 1 && v <= 10) cand_push(p + i, v);
            }
            if ((size_t) got < w) break;
        }
    }
    fclose(mf);
    free(buf);
}

/** 一轮过滤：值仍在 [0,10]，且增量在 [0,1]（不可能是别人的资源在涨） */
static void filter_round(void) {
    size_t j = 0;
    for (size_t i = 0; i < ncand; i++) {
        int32_t v = 0;
        if (rdm(cand[i].addr, &v, 4) != 4) continue;
        if (v < 0 || v > 10) continue;
        int d = v - cand[i].prev;
        if (d < 0 || d > 1) continue;
        cand[j].addr = cand[i].addr;
        cand[j].prev = v;
        j++;
    }
    ncand = j;
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s <pid> [rounds]\n", argv[0]);
        return 2;
    }
    TARGET = (pid_t) atoi(argv[1]);
    int rounds = argc > 2 ? atoi(argv[2]) : 4;
    if (rounds < 1) rounds = 4;

    scan_all();
    fprintf(stderr, "初筛 %zu\n", ncand);

    for (int r = 0; r < rounds && ncand > 0; r++) {
        usleep(2800 * 1000);
        filter_round();
        fprintf(stderr, "第 %d 轮后 %zu\n", r + 1, ncand);
        if (ncand > 4096) {
            /* 候选太多，多等一轮无意义，继续筛 */
        }
    }

    for (size_t i = 0; i < ncand; i++) {
        printf("%llu %d\n", (unsigned long long) cand[i].addr, cand[i].prev);
    }
    free(cand);
    return 0;
}