import matplotlib.pyplot as plt
import random
import math
from scipy.stats import norm

def box_muller_scaled(mean=32, std_dev=3):
    u1 = random.random()
    u2 = random.random()
    z0 = math.sqrt(-2 * math.log(u1)) * math.cos(2 * math.pi * u2)
    value = z0 * std_dev + mean
    return value

# Generate 1,000,000 samples
samples = [box_muller_scaled() for _ in range(1_000_000)]

# Plot histogram
plt.hist(samples, bins=30, edgecolor='black', color='skyblue')
plt.title('Prices of books to be generated')
plt.xlabel('Value')
plt.ylabel('Frequency')
plt.grid(True)
plt.show()

