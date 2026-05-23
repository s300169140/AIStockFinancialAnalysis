// The full 10-section prompt template. Replace [TICKER]/[COMPANY]/[COMPANY/TICKER]
// at request time. Kept server-side so we can iterate without an app release.

const TEMPLATE = `1. Complete Wall Street–Style Stock Analysis
Act as a senior Wall Street equity research analyst. Analyze the stock: [TICKER]. Include: business model and revenue streams, competitive advantages (moat), industry trends, financial health, key risks, valuation comparison, multiple scenario analysis, and a 12–24 month outlook.

2. Deep Financial Analysis
Analyze the past 5 years of financial data for [COMPANY/TICKER]. Break down: revenue growth, net profit trend, free cash flow, profit margins, debt levels, and return on equity. Conclude whether the company's financials are strong.

3. Competitive Advantage (Moat) Analysis
Evaluate the competitive moat of [COMPANY]. Discuss: brand strength, network effects, switching costs, cost advantages, and patents or proprietary technology. Compare against competitors and give the moat a score from 1–10.

4. Valuation Analysis
Value [COMPANY/TICKER] using multiple methods: P/E, P/S, EV/EBITDA, and DCF. Compare to industry peers and historical averages, then state whether the stock is undervalued, fairly valued, or overvalued, with a target price range.

5. Risk Analysis
Identify the biggest risks of investing in [COMPANY]. Include: economic risk, industry disruption, competitive threats, regulatory risk, and debt or financial risk. Rank them by severity.

6. Growth Potential Analysis
Analyze the future growth potential of [COMPANY]. Consider: market size, industry growth rate, expansion opportunities, new products, and AI or technological edge. Estimate growth potential over the next 5–10 years.

7. Institutional Investor Perspective
Act as a hedge fund portfolio manager. Assess whether [TICKER] is a good long-term investment. Include: why institutions would buy, why they'd avoid it, key catalysts, and the core investment thesis.

8. Bull vs. Bear Debate
Create a debate between two analysts on [TICKER]. One is bullish, one is bearish. Each must present data-backed arguments. End with a balanced conclusion.

9. Earnings Report Analysis
Interpret the latest earnings report for [COMPANY]. Analyze: revenue vs. expectations, profit vs. expectations, key metrics investors care about, management guidance, and market reaction.

10. Should I Buy This Stock?
Evaluate whether [TICKER] is a good investment today. Include: short-term outlook (1 year), long-term outlook (5+ years), key catalysts, major risks, and a final verdict: Buy, Hold, or Avoid.

Format your response in clean markdown with H2 headings for each section. Be concise but thorough.`

export function buildPrompt(ticker: string): string {
  const T = ticker.toUpperCase()
  return TEMPLATE.split('[TICKER]').join(T)
                 .split('[COMPANY/TICKER]').join(T)
                 .split('[COMPANY]').join(T)
}
