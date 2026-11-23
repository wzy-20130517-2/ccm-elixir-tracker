/*
 * rd4 —— 读一个 4 字节整数。给圣水轮询用（比 crexec 快得多）。
 *
 * usage: rd4 <pid> <addr> [count]
 * 输出：每行一个 i32
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <sys/uio.h>

int main(int argc, char **argv) {
    if (argc < 3) {
        fprintf(stderr, "usage: %s <pid> <addr> [count]\n", argv[0]);
        return 2;
    }
    pid_t target = (pid_t) atoi(argv[1]);
    uint64_t addr = strtoull(argv[2], NULL, 0);
    int count = argc > 3 ? atoi(argv[3]) : 1;
    if (count < 1 || count > 64) count = 1;

    int32_t vals[64];
    struct iovec l = {vals, sizeof(int32_t) * (size_t) count};
    struct iovec r = {(void *) (uintptr_t) addr, l.iov_len};
    ssize_t got = process_vm_readv(target, &l, 1, &r, 1, 0);
    if (got != (ssize_t) l.iov_len) return 1;

    for (int i = 0; i < count; i++) printf("%d\n", vals[i]);
    return 0;
}