
local grid = scut.get_node("warp_grid")

scut.grid_nudge(grid, 5, 4, 0.06, 0.00)
scut.grid_nudge(grid, 3, 4, -0.06, 0.00)
scut.grid_nudge(grid, 4, 2, 0.00, -0.04)
scut.grid_nudge(grid, 4, 6, 0.00, 0.04)
