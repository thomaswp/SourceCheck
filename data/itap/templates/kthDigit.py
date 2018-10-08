def kthDigit(x, k):
    return (x // (10 ** (k - 1))) % 10
