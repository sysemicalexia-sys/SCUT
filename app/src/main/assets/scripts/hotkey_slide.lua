
local transform = scut.get_node("transform") or scut.add_node("transform")

scut.hotkey("slide_in", function()
    scut.animate(transform, "x", -1.0, 0.0, 0.45)
end)
