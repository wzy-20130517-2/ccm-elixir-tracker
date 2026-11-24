/*
 * elixir_find —— 定位双方圣水地址。
 *
 * 圣水特征：每 2.8s +1，范围 [0,10]，出牌时 -N。
 * 敌方和我方是两个独立的计数器，都在涨，所以单靠"每 2.8s +1"分不开。
 *
 * 区分办法：**出牌时只有一个会掉**。
 *   mode=find    只筛候选，输出所有地址 + 当前值
 *   mode=watch   持续监控给定地址，打印变化（用于出牌时观察哪个掉了）
 *
 * usage:
 *   elixir_find <pid> find [rounds]
 *   elixir_find <pid> watch <addr1> <addr2> ... [seconds]
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/uio.h>

static pid_t TARGET;

static int rd_i32(uint64_t addr, int32_t *out) {
    struct iovec l = {out, 4};
    struct iovec r = {(void *) (uintptr_t) addr, 4};
    return process_vm_readv(TARGET, &l, 1, &r, 1, 0) == 4 ? 0 : -1;
}

typedef struct { uint64_t addr; int32_t prev; } Cand;
#define MAX_CAND 3000000
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

static void scan_all(void) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/maps", TARGET);
    FILE *mf = fopen(path, "r");
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
                /* 圣水可能是整数，也可能存成 0..10 的定点数；这里先按整数收 */
                if (v >= 1 && v <= 10) cand_push(p + i, v);
            }
            if ((size_t) got < w) break;
        }
    }
    fclose(mf);
    free(buf);
}

/** 一轮：值仍 [0,10]，且增量 0..1（圣水只涨不跳） */
static void filter_round(void) {
    size_t j = 0;
    for (size_t i = 0; i < ncand; i++) {
        int32_t v;
        if (rd_i32(cand[i].addr, &v) < 0) continue;
        if (v < 0 || v > 10) continue;
        int d = v - cand[i].prev;
        if (d < 0 || d > 1) continue;
        cand[j].addr = cand[i].addr;
        cand[j].prev = v;
        j++;
    }
    ncand = j;
}

static int mode_find(int rounds) {
    scan_all();
    fprintf(stderr, "初筛 %zu\n", ncand);
    for (int r = 0; r < rounds && ncand > 0; r++) {
        usleep(2800 * 1000);
        filter_round();
        fprintf(stderr, "第 %d 轮后 %zu\n", r + 1, ncand);
    }
    for (size_t i = 0; i < ncand; i++)
        printf("%llu %d\n", (unsigned long long) cand[i].addr, cand[i].prev);
    return 0;
}

static int mode_watch(int argc, char **argv, int from) {
    int n = argc - from;
    if (n <= 0 || n > 16) {
        fprintf(stderr, "watch 需要 1..16 个地址\n");
        return 2;
    }
    uint64_t addrs[16];
    int32_t last[16];
    for (int i = 0; i < n; i++) {
        addrs[i] = strtoull(argv[from + i], NULL, 0);
        if (rd_i32(addrs[i], &last[i]) < 0) last[i] = -1;
    }
    fprintf(stderr, "监控 %d 个地址，出牌时观察哪个掉了\n", n);
    for (int tick = 0; tick < 300; tick++) {
        usleep(100 * 1000);
        for (int i = 0; i < n; i++) {
            int32_t v;
            if (rd_i32(addrs[i], &v) < 0) continue;
            if (v != last[i]) {
                printf("%d %llu %d %d\n", tick, (unsigned long long) addrs[i], last[i], v);
                fflush(stdout);
                last[i] = v;
            }
        }
    }
    return 0;
}

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr,
                "usage:\n  %s <pid> find [rounds]\n  %s <pid> watch <addr...> \n",
                argv[0], argv[0]);
        return 2;
    }
    TARGET = (pid_t) atoi(argv[1]);
    const char *mode = argv[2];

    if (strcmp(mode, "find") == 0) {
        int rounds = argc > 3 ? atoi(argv[3]) : 3;
        return mode_find(rounds < 1 ? 3 : rounds);
    }
    if (strcmp(mode, "watch") == 0) return mode_watch(argc, argv, 3);

    fprintf(stderr, "未知模式: %s\n", mode);
    return 2;
}