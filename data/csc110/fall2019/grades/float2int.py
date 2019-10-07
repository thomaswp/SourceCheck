import pandas as pd
filename = 'brickWallRaw.csv'

data = pd.read_csv(filename, index_col = 0)
data = data.astype(int)

for i in data.index:
    if data.ix[i, 'Grade'] == 100:
        data.ix[i, 'Grade'] = 2
    else:
        data.ix[i, 'Grade'] = 1

data.to_csv('brickWall.csv')
